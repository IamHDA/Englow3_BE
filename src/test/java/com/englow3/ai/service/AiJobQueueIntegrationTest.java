package com.englow3.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.ai.entity.AiJob;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * The daily provider budget, counted where every provider call passes.
 * <p>
 * Speaking and the tutor used to count their own work against the same setting, each against the full limit, so a
 * learner could make twice the calls it was meant to allow. The mocked service tests could not show that: each saw only
 * its own counter and each was correct about it. The bug was in the sum, and the sum only exists here.
 */
class AiJobQueueIntegrationTest extends PostgresIntegrationTest {

    private static final int LIMIT = 3;

    @Autowired
    private AiJobQueue queue;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AiJobWorkerQueue workerQueue;

    private AiJobQueue target;
    private int originalLimit;
    private UUID learner;

    @BeforeEach
    void setUp() {
        // The bean is a transactional proxy; the field lives on the object behind it.
        target = AopTestUtils.getUltimateTargetObject(queue);
        originalLimit = (int) ReflectionTestUtils.getField(target, "dailyRequestLimit");
        ReflectionTestUtils.setField(target, "dailyRequestLimit", LIMIT);

        learner = new LearnerFixture(jdbc).learner();
    }

    @AfterEach
    void restoreLimit() {
        // The context is shared across the suite; leaving the limit at three would leak into every later test.
        ReflectionTestUtils.setField(target, "dailyRequestLimit", originalLimit);
    }

    private void speak(UUID userId) {
        UUID target = UUID.randomUUID();
        queue.enqueueSpeechAssessment(target, "{}", "speech:" + target, "v1", userId);
    }

    private void ask(UUID userId) {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", userId);
    }

    /** The bug itself. Two assessments and one question are three requests, whichever screens they came from. */
    @Test
    void countsEveryFeatureAgainstOneBudget() {
        speak(learner);
        speak(learner);
        assertThat(queue.hasDailyAllowance(learner)).isTrue();

        ask(learner);

        assertThat(queue.hasDailyAllowance(learner)).isFalse();
    }

    @Test
    void keepsOneLearnersSpendingOffAnothersBudget() {
        UUID other = new LearnerFixture(jdbc).learner();
        speak(other);
        speak(other);
        ask(other);

        assertThat(queue.hasDailyAllowance(learner)).isTrue();
    }

    /**
     * A second submit of the same work is the same job, not a second request. Counting it would charge a learner for a
     * double click.
     */
    @Test
    void doesNotChargeTwiceForTheSameWork() {
        UUID attempt = UUID.randomUUID();
        for (int i = 0; i < LIMIT + 2; i++) {
            queue.enqueueSpeechAssessment(attempt, "{}", "speech:" + attempt, "v1", learner);
        }

        assertThat(queue.hasDailyAllowance(learner)).isTrue();
    }

    /** The budget is per day, the learners' day like every other daily figure here. Yesterday's spending is spent. */
    @Test
    void startsAgainEachDay() {
        speak(learner);
        speak(learner);
        ask(learner);
        jdbc.sql("update ai_jobs set created_at = now() - interval '1 day' where requested_by_user_id = :userId")
                .param("userId", learner).update();

        assertThat(queue.hasDailyAllowance(learner)).isTrue();
    }

    /** Recorded on the row, so the budget reads what was actually stored rather than what a caller claimed. */
    @Test
    void recordsWhoAskedOnTheJob() {
        ask(learner);

        Long attributed = jdbc.sql("select count(*) from ai_jobs where requested_by_user_id = :userId")
                .param("userId", learner).query(Long.class).single();

        assertThat(attributed).isEqualTo(1);
    }

    /**
     * A worker slow enough to have its job reclaimed still reports in. Its late answer must not land: a late transient
     * failure used to put a job back in the queue after another worker had already finished it, to be run - and paid
     * for - again.
     */
    @Test
    void dropsALateOutcomeFromAClaimThatWasReclaimed() {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        AiJob claimed = workerQueue.claimBatch(1000).stream()
                .filter(candidate -> candidate.getTargetId().equals(target)).findFirst().orElseThrow();
        AiJob job = claimed;
        workerQueue.reclaimStalled(Duration.ZERO);

        boolean gaveUp = workerQueue.record(job.getId(), claimed.getStartedAt(),
                AiJobHandler.Outcome.transientFailure("PROVIDER_TIMEOUT", "late"));

        assertThat(gaveUp).isFalse();
        assertThat(statusOf(job.getId())).isEqualTo("PENDING");
    }

    @Test
    void dropsALateFailureForAJobAlreadyFinished() {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        AiJob claimed = workerQueue.claimBatch(1000).stream()
                .filter(candidate -> candidate.getTargetId().equals(target)).findFirst().orElseThrow();
        AiJob job = claimed;
        workerQueue.record(job.getId(), claimed.getStartedAt(), AiJobHandler.Outcome.succeeded("{\"content\":\"ok\"}"));

        workerQueue.record(job.getId(), claimed.getStartedAt(),
                AiJobHandler.Outcome.transientFailure("PROVIDER_TIMEOUT", "late"));

        assertThat(statusOf(job.getId())).isEqualTo("SUCCEEDED");
    }

    private String statusOf(UUID jobId) {
        return jdbc.sql("select status from ai_jobs where id = :id").param("id", jobId).query(String.class).single();
    }

    @Test
    void concurrentWorkersClaimDifferentRows() throws Exception {
        ask(learner);
        ask(learner);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor
                    .submit(() -> workerQueue.claimBatch(1, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)));
            var second = executor
                    .submit(() -> workerQueue.claimBatch(1, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)));
            assertThat(first.get()).hasSize(1);
            assertThat(second.get()).hasSize(1);
            assertThat(first.get().getFirst().getId()).isNotEqualTo(second.get().getFirst().getId());
        }
    }

    @Test
    void providerRetryAfterIsPreservedAndPreflightDeferralDoesNotSpendRetryCount() {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        var claimed = workerQueue.claimBatch(1000, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)).stream()
                .filter(job -> job.getTargetId().equals(target)).findFirst().orElseThrow();
        workerQueue.defer(claimed.getId(), claimed.getStartedAt(), Duration.ofSeconds(10));
        assertThat(jdbc.sql("select retry_count from ai_jobs where id = :id").param("id", claimed.getId())
                .query(Integer.class).single()).isZero();
        jdbc.sql("update ai_jobs set next_retry_at = null where id = :id").param("id", claimed.getId()).update();
        var next = workerQueue.claimBatch(1000, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)).stream()
                .filter(job -> job.getId().equals(claimed.getId())).findFirst().orElseThrow();
        workerQueue.record(next.getId(), next.getStartedAt(),
                AiJobHandler.Outcome.transientFailure("RATE_LIMIT", "later").retryAfter(Duration.ofSeconds(60)));
        assertThat(jdbc.sql("select next_retry_at > now() + interval '55 seconds' from ai_jobs where id = :id")
                .param("id", next.getId()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void expiredClaimCannotPublishBusinessChangesAfterReclaim() {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        AiJob claimed = workerQueue.claimBatch(1000, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)).stream()
                .filter(candidate -> candidate.getTargetId().equals(target)).findFirst().orElseThrow();
        jdbc.sql("update ai_jobs set lease_until = now() - interval '1 second' where id = :id")
                .param("id", claimed.getId()).update();
        workerQueue.reclaimStalled(Duration.ofSeconds(30));
        var published = new java.util.concurrent.atomic.AtomicBoolean();
        workerQueue.record(claimed.getId(), claimed.getStartedAt(), AiJobHandler.Outcome.succeeded("{}"),
                () -> published.set(true));
        assertThat(published).isFalse();
        assertThat(workerQueue.heartbeat(claimed.getId(), claimed.getStartedAt(), Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void businessWriteAndJobCompletionRollbackTogether() {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        AiJob claimed = workerQueue.claimBatch(1000, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)).stream()
                .filter(candidate -> candidate.getTargetId().equals(target)).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workerQueue.record(claimed.getId(),
                claimed.getStartedAt(), AiJobHandler.Outcome.succeeded("{}"), () -> {
                    throw new IllegalStateException("write failed");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(statusOf(claimed.getId())).isEqualTo("RUNNING");
        assertThat(workerQueue.heartbeat(claimed.getId(), claimed.getStartedAt(), Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void typedClaimNeverConsumesAnotherLanesJobs() {
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        assertThat(workerQueue.claimBatch(1000, java.util.List.of("SPEECH_ASSESSMENT"), Duration.ofSeconds(30)))
                .allMatch(job -> job.getJobType() == com.englow3.ai.entity.AiJobType.SPEECH_ASSESSMENT);
        assertThat(workerQueue.claimBatch(1000, java.util.List.of("TUTOR_REPLY"), Duration.ofSeconds(30)))
                .anyMatch(job -> job.getTargetId().equals(target));
    }
}
