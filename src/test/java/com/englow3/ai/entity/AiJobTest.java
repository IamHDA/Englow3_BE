package com.englow3.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.ai.service.RetryBackoff;
import com.englow3.shared.error.ConflictException;

class AiJobTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final short MAX_RETRIES = 3;

    private static AiJob pendingJob() {
        return AiJob.pending(AiJobType.SPEECH_ASSESSMENT, "SPEAKING_ATTEMPT", UUID.randomUUID(), "azure",
                "pronunciation-assessment", "v1", "{}", UUID.randomUUID().toString(), MAX_RETRIES);
    }

    private static AiJob runningJob() {
        AiJob job = pendingJob();
        job.claim(NOW);
        return job;
    }

    @Nested
    class Claiming {

        @Test
        void startsPendingWithNothingAttempted() {
            AiJob job = pendingJob();

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.getRetryCount()).isZero();
            assertThat(job.getStartedAt()).isNull();
            assertThat(job.finished()).isFalse();
        }

        @Test
        void marksTheJobRunningAndStampsTheStart() {
            AiJob job = runningJob();

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.RUNNING);
            assertThat(job.getStartedAt()).isEqualTo(NOW);
        }

        /** Two workers that both read the row before either wrote must not both proceed. */
        @Test
        void refusesToClaimAJobSomebodyElseTook() {
            AiJob job = runningJob();

            assertThatThrownBy(() -> job.claim(NOW)).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("AI_JOB_NOT_PENDING");
        }
    }

    @Nested
    class Finishing {

        @Test
        void keepsTheOutputAndClearsAnyEarlierError() {
            AiJob job = pendingJob();
            job.fail("PROVIDER_UNAVAILABLE", "503", true, NOW);
            job.claim(NOW);

            job.succeed("{\"accuracy\":88}", NOW);

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.SUCCEEDED);
            assertThat(job.getOutputPayload()).isEqualTo("{\"accuracy\":88}");
            assertThat(job.getErrorCode()).isNull();
            assertThat(job.getNextRetryAt()).isNull();
            assertThat(job.finished()).isTrue();
        }

        @Test
        void schedulesAnotherAttemptAfterARetryableFailure() {
            AiJob job = runningJob();

            job.fail("PROVIDER_UNAVAILABLE", "503 from the provider", true, NOW);

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.getRetryCount()).isEqualTo((short) 1);
            assertThat(job.getNextRetryAt()).isEqualTo(NOW.plus(RetryBackoff.delayAfter(0)));
            assertThat(job.finished()).isFalse();
        }

        /**
         * A recording the provider cannot parse will not become parseable in ten minutes. Spending the retry budget on
         * it delays every other job in the queue to reach the same answer.
         */
        @Test
        void givesUpAtOnceOnSomethingThatCannotSucceedLater() {
            AiJob job = runningJob();

            job.fail("UNSUPPORTED_AUDIO_TYPE", "MP3 is not accepted", false, NOW);

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(job.getCompletedAt()).isEqualTo(NOW);
            assertThat(job.getNextRetryAt()).isNull();
        }

        @Test
        void stopsRetryingOnceTheBudgetIsSpent() {
            AiJob job = pendingJob();

            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                job.claim(NOW);
                job.fail("PROVIDER_UNAVAILABLE", "503", true, NOW);
            }

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(job.getRetryCount()).isEqualTo(MAX_RETRIES);
            assertThat(job.getErrorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        }
    }

    @Nested
    class Stalling {

        /** A worker that died leaves a RUNNING row nobody will finish. */
        @Test
        void putsAStalledJobBackInTheQueue() {
            AiJob job = runningJob();

            job.reclaim(NOW);

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.getErrorCode()).isEqualTo("AI_JOB_STALLED");
        }

        /**
         * A stall costs a retry. The work may have reached the provider before the worker died, and a free reclaim
         * would let one crash-looping instance replay the same job without limit.
         */
        @Test
        void countsAStallAgainstTheRetryBudget() {
            AiJob job = runningJob();

            job.reclaim(NOW);

            assertThat(job.getRetryCount()).isEqualTo((short) 1);
        }

        @Test
        void refusesToReclaimAJobNobodyIsRunning() {
            AiJob job = pendingJob();

            assertThatThrownBy(() -> job.reclaim(NOW)).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("AI_JOB_NOT_RUNNING");
        }
    }
}
