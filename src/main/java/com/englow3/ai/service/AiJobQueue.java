package com.englow3.ai.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobStatus;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.repository.AiJobRepository;

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
public class AiJobQueue {

    private final AiJobRepository jobRepo;

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

    /** Three attempts covers a provider blip without leaving a learner waiting through four backoffs. */
    private static final short MAX_RETRY_COUNT = 3;

    /**
     * Whether this learner may cause another provider call today.
     * <p>
     * A question rather than a refusal, so each feature keeps deciding when to ask and how to say no - speaking asks at
     * submission, after the recording exists, and the tutor before the question is stored. The day is UTC, like every
     * other daily figure in this schema.
     */
    @Transactional(readOnly = true)
    public boolean hasDailyAllowance(UUID userId) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return jobRepo.countRequestedSince(userId, startOfDay) < dailyRequestLimit;
    }

    /** The ceiling, so a refusal can say what it was. */
    public int dailyRequestLimit() {
        return dailyRequestLimit;
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
    @Transactional
    public AiJob enqueue(AiJobType jobType, String targetType, UUID targetId, String inputPayload,
            String idempotencyKey, String promptVersion, UUID requestedByUserId) {
        return jobRepo.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            AiJob job = AiJob.pending(jobType, targetType, targetId, providerName, defaultModel, promptVersion,
                    inputPayload, idempotencyKey, MAX_RETRY_COUNT, requestedByUserId);
            try {
                return jobRepo.saveAndFlush(job);
            } catch (DataIntegrityViolationException raced) {
                return jobRepo.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> raced);
            }
        });
    }

    /**
     * Takes up to {@code limit} jobs and marks them RUNNING, in one short transaction. The provider is called after
     * this commits, never inside it: a database connection held for the length of a network round trip is how a pool
     * runs dry under load.
     */
    @Transactional
    public List<AiJob> claimBatch(int limit) {
        Instant now = Instant.now();
        List<AiJob> claimed = jobRepo.lockNextPending(now, limit);
        claimed.forEach(job -> job.claim(now));

        return claimed;
    }

    /**
     * Writes the result of one attempt. Separate transaction from the claim, and from the provider call.
     *
     * @return true when this attempt was the job's last and it is now finished without a result - the caller then owes
     *         the job's module a {@link AiJobHandler#onGaveUp}
     */
    @Transactional
    public boolean record(UUID jobId, AiJobHandler.Outcome outcome) {
        return jobRepo.findById(jobId).map(job -> {
            if (outcome.success()) {
                job.succeed(outcome.outputPayload(), Instant.now());
            } else {
                job.fail(outcome.errorCode(), outcome.errorMessage(), outcome.retryable(), Instant.now());
            }
            return job.getStatus() == AiJobStatus.FAILED;
        }).orElse(false);
    }

    /** Puts jobs whose worker vanished back in the queue. Each costs a retry - see {@code AiJob.reclaim}. */
    @Transactional
    public List<AiJob> reclaimStalled(Duration lockTimeout) {
        Instant now = Instant.now();
        List<AiJob> stalled = jobRepo.findStalled(now.minus(lockTimeout));
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
}
