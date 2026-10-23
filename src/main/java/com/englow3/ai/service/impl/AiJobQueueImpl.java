package com.englow3.ai.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobStatus;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.repository.AiJobRepository;
import com.englow3.ai.service.AiJobWorkerQueue;
import com.englow3.ai.service.AiJobQueued;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.subscription.api.PlanEntitlements;

import lombok.RequiredArgsConstructor;

/**
 * The queue itself: the only thing that writes {@code ai_jobs}.
 * <p>
 * This is also the cross-module entry point. A module wanting AI work done calls {@link #enqueue} and gets a row back;
 * it never touches the table and never calls a provider itself. Same shape as {@code UserDirectory}: one narrow service
 * other modules are allowed to depend on.
 */
@Service
@RequiredArgsConstructor
public class AiJobQueueImpl implements AiJobQueue, AiJobWorkerQueue {

    private static final Logger log = LoggerFactory.getLogger(AiJobQueue.class);

    private final AiJobRepository jobRepo;
    private final StudyCalendar calendar;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final PlanEntitlements plans;

    @Value("${app.ai.queue-scope:local}")
    private String queueScope = "testing";
    @Value("${app.ai.max-outstanding-jobs:200}")
    private int maxOutstandingJobs = 200;

    @Override
    @Transactional(readOnly = true)
    public Operations operations() {
        var stats = jobRepo.queueStats(queueScope, calendar.startOfToday());
        Metrics metrics = stats == null ? null
                : new Metrics(stats.getSamples(), stats.getQueueP95Ms(), stats.getCompletionP95Ms(),
                        stats.getKnownInputTokens(), stats.getKnownOutputTokens(), stats.getUnknownUsageJobs(),
                        jobRepo.terminalFailures(queueScope, calendar.startOfToday()).stream()
                                .map(f -> new FailureCount(f.getCode(), f.getCount())).toList());
        return new Operations(jobRepo.countByQueueScopeAndStatus(queueScope, AiJobStatus.PENDING),
                jobRepo.countByQueueScopeAndStatus(queueScope, AiJobStatus.RUNNING),
                jobRepo.countByQueueScopeAndStatusAndCompletedAtGreaterThanEqual(queueScope, AiJobStatus.SUCCEEDED,
                        calendar.startOfToday()),
                jobRepo.countByQueueScopeAndStatusAndCompletedAtGreaterThanEqual(queueScope, AiJobStatus.FAILED,
                        calendar.startOfToday()),
                jobRepo.findFirstByQueueScopeAndStatusOrderByCreatedAtAsc(queueScope, AiJobStatus.PENDING)
                        .map(AiJob::getCreatedAt).orElse(null),
                queueScope, metrics);
    }

    @Override
    @Transactional
    public void lockBudget(UUID userId) {
        jobRepo.lockProductiveBudget(userId.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public Usage usage(UUID userId) {
        long used = jobRepo.countRequestedSince(userId, calendar.startOfToday());
        return new Usage(dailyRequestLimit(userId), (int) Math.min(Integer.MAX_VALUE, used),
                calendar.today().plusDays(1).atStartOfDay(calendar.zone()).toInstant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFailedTutorReply(UUID messageId, int revision) {
        String key = "TUTOR_MESSAGE:" + messageId + (revision == 0 ? "" : ":" + revision);
        return jobRepo.findByIdempotencyKey(key).filter(job -> queueScope.equals(job.getQueueScope()))
                .map(job -> job.getStatus() == AiJobStatus.FAILED).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFailedTutorTranscription(UUID recordingId) {
        return jobRepo.findByIdempotencyKey("TUTOR_AUDIO:" + recordingId)
                .filter(job -> queueScope.equals(job.getQueueScope())).map(job -> job.getStatus() == AiJobStatus.FAILED)
                .orElse(false);
    }

    @Override
    @Transactional
    public void enqueueTutorTranscription(UUID recordingId, String payload, String idempotencyKey, UUID userId) {
        lockBudget(userId);
        if (jobRepo.findByIdempotencyKey(idempotencyKey).isEmpty()) {
            if (!hasDailyAllowance(userId))
                throw new ConflictException("TUTOR_DAILY_LIMIT_REACHED",
                        "Today's shared AI allowance has been reached");
            if (jobRepo.countRequestedSince(userId, clock.instant().minusSeconds(60)) >= 10)
                throw new ConflictException("TUTOR_RATE_LIMIT", "Please wait a minute before trying again");
        }
        enqueue(AiJobType.TUTOR_TRANSCRIPTION, "TUTOR_RECORDING", recordingId, payload, idempotencyKey,
                "tutor-transcription-v1", userId);
    }

    @Override
    @Transactional
    public void purgeTutorRecording(UUID recordingId, UUID userId) {
        jobRepo.lockOwnedTarget("TUTOR_RECORDING", recordingId, userId)
                .forEach(job -> job.purgeContent(clock.instant()));
    }

    @Override
    @Transactional
    public void purgeTutorData(UUID userId) {
        lockBudget(userId);
        jobRepo.lockTutorDataForUser(userId).forEach(job -> job.purgeContent(clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFailedProductiveAssessment(UUID attemptId, int revision) {
        return jobRepo.findByIdempotencyKey("productive:" + attemptId + ":" + revision)
                .map(job -> job.getStatus() == AiJobStatus.FAILED).orElse(false);
    }

    @Override
    @Transactional
    public void enqueueProductiveAssessment(UUID attemptId, String inputPayload, String idempotencyKey,
            String promptVersion, UUID requestedByUserId) {
        jobRepo.lockProductiveBudget(requestedByUserId.toString());
        if (jobRepo.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        if (!hasDailyAllowance(requestedByUserId)) {
            throw new ConflictException("ASSESSMENT_DAILY_LIMIT", "Today's AI allowance has been reached");
        }
        enqueue(AiJobType.PRODUCTIVE_ASSESSMENT, "ASSESSMENT_ATTEMPT", attemptId, inputPayload, idempotencyKey,
                promptVersion, requestedByUserId);
    }

    @Value("${app.ai.provider:ai-service}")
    private String providerName;

    @Value("${app.ai.default-model:}")
    private String defaultModel;

    /**
     * Provider calls one learner may cause in a day, across every feature.
     * <p>
     * Owned here rather than by the features that spend it. Speaking and the tutor each used to count their own work
     * against this same setting, which let a learner make twice the calls it was meant to allow; every provider call
     * goes through this queue, so this is the one place a total exists.
     */
    @Value("${app.ai.daily-request-limit:100}")
    private int dailyRequestLimit;

    /** Requests the whole platform may cause in a day, across every learner. Zero means no platform ceiling. */
    @Value("${app.ai.platform-daily-request-limit:0}")
    private int platformDailyRequestLimit;

    /** Three attempts covers a provider blip without leaving a learner waiting through four backoffs. */
    private static final short MAX_RETRY_COUNT = 3;

    /**
     * Whether this learner may cause another provider call today.
     * <p>
     * A question rather than a refusal, so each feature keeps deciding when to ask and how to say no - speaking asks at
     * submission, after the recording exists, and the tutor before the question is stored. The day is the learners'
     * day, like every other daily figure - see {@link StudyCalendar}.
     */
    @Transactional(readOnly = true)
    public boolean hasDailyAllowance(UUID userId) {
        return !platformCeilingReached()
                && jobRepo.countRequestedSince(userId, calendar.startOfToday()) < dailyRequestLimit(userId);
    }

    /**
     * Whether the platform as a whole has spent its day. A per-learner limit bounds one person; this bounds the bill -
     * many accounts each inside their own allowance can still add up to more than the provider plan covers. Zero turns
     * it off.
     */
    private boolean platformCeilingReached() {
        if (platformDailyRequestLimit <= 0) {
            return false;
        }
        long spent = jobRepo.countByQueueScopeAndCreatedAtGreaterThanEqual(queueScope, calendar.startOfToday());
        if (spent >= platformDailyRequestLimit) {
            log.warn("Platform AI ceiling reached for scope {}: {} of {} requests today", queueScope, spent,
                    platformDailyRequestLimit);
            return true;
        }
        return false;
    }

    /** The ceiling on the free plan, so a refusal that has no learner in hand can still say what it was. */
    public int dailyRequestLimit() {
        return dailyRequestLimit;
    }

    /** The ceiling for this learner: the paid plan's while it is in force, the free plan's otherwise. */
    public int dailyRequestLimit(UUID userId) {
        return plans.hasPaidPlan(userId) ? Math.max(dailyRequestLimit, plans.paidAiDailyRequestLimit())
                : dailyRequestLimit;
    }

    /**
     * Records the intent to do some work, and returns immediately.
     * <p>
     * {@code requestedByUserId} is required and is what {@link #hasDailyAllowance} counts. A caller that passed null
     * for work a learner caused would be handing out calls the budget never sees.
     * <p>
     * Idempotent on {@code idempotencyKey}: asking twice gives the same row rather than two. The check is a lookup
     * first and a caught constraint violation second, because between the lookup and the insert another request can win
     * - the unique index is what actually enforces it, and this only turns the resulting error into the answer the
     * caller wanted.
     */
    private AiJob enqueue(AiJobType jobType, String targetType, UUID targetId, String inputPayload,
            String idempotencyKey, String promptVersion, UUID requestedByUserId) {
        return jobRepo.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            jobRepo.lockProductiveBudget("queue:" + queueScope);
            long outstanding = jobRepo.countByQueueScopeAndStatus(queueScope, AiJobStatus.PENDING)
                    + jobRepo.countByQueueScopeAndStatus(queueScope, AiJobStatus.RUNNING);
            if (outstanding >= maxOutstandingJobs)
                throw new ConflictException("AI_QUEUE_BUSY", "The AI queue is busy; please try again shortly");
            AiJob job = AiJob.pending(jobType, targetType, targetId, providerName, defaultModel, promptVersion,
                    inputPayload, idempotencyKey, MAX_RETRY_COUNT, requestedByUserId);
            job.scopeTo(queueScope);
            try {
                var saved = jobRepo.saveAndFlush(job);
                events.publishEvent(new AiJobQueued());
                return saved;
            } catch (DataIntegrityViolationException raced) {
                return jobRepo.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> raced);
            }
        });
    }

    @Override
    @Transactional
    public void enqueueSpeechAssessment(UUID attemptId, String inputPayload, String idempotencyKey,
            String promptVersion, UUID requestedByUserId) {
        lockBudget(requestedByUserId);
        if (jobRepo.findByIdempotencyKey(idempotencyKey).isEmpty() && !hasDailyAllowance(requestedByUserId)) {
            throw new ConflictException("SPEECH_DAILY_LIMIT_REACHED", "Today's AI allowance has been reached");
        }
        enqueue(AiJobType.SPEECH_ASSESSMENT, "SPEAKING_ATTEMPT", attemptId, inputPayload, idempotencyKey, promptVersion,
                requestedByUserId);
    }

    @Override
    @Transactional
    public void enqueueTutorReply(UUID messageId, String inputPayload, String idempotencyKey, String promptVersion,
            UUID requestedByUserId) {
        lockBudget(requestedByUserId);
        if (jobRepo.findByIdempotencyKey(idempotencyKey).isEmpty()) {
            if (!hasDailyAllowance(requestedByUserId)) {
                throw new ConflictException("TUTOR_DAILY_LIMIT_REACHED", "Today's AI allowance has been reached");
            }
            if (jobRepo.countRequestedSince(requestedByUserId, clock.instant().minusSeconds(60)) >= 10) {
                throw new ConflictException("TUTOR_RATE_LIMIT", "Please wait a minute before asking again");
            }
        }
        enqueue(AiJobType.TUTOR_REPLY, "TUTOR_MESSAGE", messageId, inputPayload, idempotencyKey, promptVersion,
                requestedByUserId);
    }

    /**
     * Takes up to {@code limit} jobs and marks them RUNNING, in one short transaction. The provider is called after
     * this commits, never inside it: a database connection held for the length of a network round trip is how a pool
     * runs dry under load.
     */
    @Transactional
    public List<AiJob> claimBatch(int limit) {
        return claimBatch(limit, java.util.Arrays.stream(AiJobType.values()).map(Enum::name).toList(),
                Duration.ofMinutes(3));
    }

    @Override
    @Transactional
    public List<AiJob> claimBatch(int limit, List<String> types, Duration lease) {
        if (limit < 1 || types.isEmpty())
            return List.of();
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        List<AiJob> claimed = jobRepo.lockNextByTypes(now, limit, types, queueScope);
        claimed.forEach(job -> {
            job.claim(now);
            job.renewLease(now.plus(lease));
            log.info("AI job claimed id={} type={} queueMs={}", job.getId(), job.getJobType(),
                    job.getCreatedAt() == null ? 0 : Duration.between(job.getCreatedAt(), now).toMillis());
        });
        return claimed;
    }

    @Override
    @Transactional
    public boolean heartbeat(UUID jobId, Instant claimedAt, Duration lease) {
        return jobRepo.heartbeat(jobId, claimedAt, clock.instant().plus(lease)) == 1;
    }

    @Override
    @Transactional
    public void defer(UUID jobId, Instant claimedAt, Duration delay) {
        jobRepo.lockById(jobId)
                .filter(job -> job.getStatus() == AiJobStatus.RUNNING && sameInstant(job.getStartedAt(), claimedAt))
                .ifPresent(job -> job.releaseUntil(clock.instant().plus(delay)));
    }

    /**
     * Writes the result of one attempt. Separate transaction from the claim, and from the provider call.
     *
     * @return true when this attempt was the job's last and it is now finished without a result - the caller then owes
     *         the job's module a {@link AiJobHandler#onGaveUp}
     */
    @Transactional
    public boolean record(UUID jobId, AiJobHandler.Outcome outcome) {
        return record(jobId, null, outcome);
    }

    /**
     * As above, for the attempt that claimed the job at {@code claimedAt}.
     * <p>
     * A worker slow enough to have its job reclaimed still finishes, and its answer arrives after the job has moved on
     * - retried by another worker, finished, or given up. Applied anyway, a late transient failure put a job that
     * another worker had already completed back in the queue to run, and be paid for, again. So an outcome only lands
     * while the job is still running under the claim that produced it; otherwise it is dropped.
     */
    @Override
    @Transactional
    public boolean record(UUID jobId, Instant claimedAt, AiJobHandler.Outcome outcome) {
        return record(jobId, claimedAt, outcome, () -> {
        });
    }

    @Override
    @Transactional
    public boolean record(UUID jobId, Instant claimedAt, AiJobHandler.Outcome outcome, Runnable complete) {
        Instant now = clock.instant();
        return jobRepo.lockById(jobId).map(job -> {
            if (job.getStatus() != AiJobStatus.RUNNING
                    || (claimedAt != null && !sameInstant(job.getStartedAt(), claimedAt))) {
                log.warn("Dropped a late outcome for AI job {}: it is {} and no longer under that claim", jobId,
                        job.getStatus());
                return false;
            }
            if (outcome.success()) {
                complete.run();
                job.succeed(outcome.outputPayload(), now);
            } else {
                job.fail(outcome.errorCode(), outcome.errorMessage(), outcome.retryable(), now);
                if (outcome.retryable())
                    job.deferRetry(now.plus(com.englow3.ai.service.RetryBackoff.delayAfter(job.getRetryCount() - 1))
                            .plusMillis(java.util.concurrent.ThreadLocalRandom.current().nextLong(1000)));
                if (outcome.retryAfter() != null)
                    job.deferRetry(now.plus(outcome.retryAfter()));
            }
            return job.getStatus() == AiJobStatus.FAILED;
        }).orElse(false);
    }

    /** Puts jobs whose worker vanished back in the queue. Each costs a retry - see {@code AiJob.reclaim}. */
    @Transactional
    public List<AiJob> reclaimStalled(Duration lockTimeout) {
        Instant now = clock.instant();
        List<AiJob> stalled = jobRepo.lockStalled(now, now.minus(lockTimeout), queueScope);
        stalled.forEach(job -> job.reclaim(now));

        // Returned rather than counted: a reclaim costs a retry, so one of these may have just run out, and the worker
        // has to tell that job's module it is over.
        return stalled;
    }

    /** How the module that asked can find out how its work went. Read-only; it does not touch other modules' tables. */
    @Transactional(readOnly = true)
    public List<AiJob> jobsFor(String targetType, UUID targetId) {
        return jobRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }

    /**
     * The same claim, to within the microsecond the column keeps. Within, not truncated: Postgres rounds to the
     * microsecond, so a claim made at .1234567 is stored as .123457 and truncating the in-memory value to .123456 would
     * call it a different claim.
     */
    private static boolean sameInstant(Instant stored, Instant claimed) {
        return stored != null && Duration.between(stored, claimed).abs().compareTo(ONE_MICROSECOND) <= 0;
    }

    private static final Duration ONE_MICROSECOND = Duration.ofNanos(1_000);
}
