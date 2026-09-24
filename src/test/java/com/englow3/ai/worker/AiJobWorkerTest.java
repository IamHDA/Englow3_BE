package com.englow3.ai.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.service.AiJobHandler;
import com.englow3.ai.service.AiJobQueue;

/**
 * What the worker does when a handler misbehaves. Each branch decides whether work is tried again, which is the whole
 * reason the queue is durable - getting it wrong either loses the work or repeats it forever.
 */
class AiJobWorkerTest {

    private final AiJobQueue queue = mock(AiJobQueue.class);

    private static AiJob job() {
        return AiJob.pending(AiJobType.SPEECH_ASSESSMENT, "SPEAKING_ATTEMPT", UUID.randomUUID(), "azure", "model", "v1",
                "{}", UUID.randomUUID().toString(), (short) 3);
    }

    private AiJobWorker workerWith(AiJobHandler... handlers) {
        return new AiJobWorker(queue, List.of(handlers), 5, Duration.ofMinutes(5));
    }

    private static AiJobHandler handlerThat(java.util.function.Function<AiJob, AiJobHandler.Outcome> behaviour) {
        return new AiJobHandler() {
            @Override
            public AiJobType handles() {
                return AiJobType.SPEECH_ASSESSMENT;
            }

            @Override
            public Outcome run(AiJob job) {
                return behaviour.apply(job);
            }
        };
    }

    private AiJobHandler.Outcome drainAndCapture(AiJob job) {
        ArgumentCaptor<AiJobHandler.Outcome> outcome = ArgumentCaptor.forClass(AiJobHandler.Outcome.class);
        verify(queue).record(eq(job.getId()), outcome.capture());

        return outcome.getValue();
    }

    @Test
    void recordsWhatTheHandlerReturned() {
        AiJob job = job();
        when(queue.claimBatch(5)).thenReturn(List.of(job));

        workerWith(handlerThat(ignored -> AiJobHandler.Outcome.succeeded("{\"ok\":true}"))).drain();

        AiJobHandler.Outcome recorded = drainAndCapture(job);
        assertThat(recorded.success()).isTrue();
        assertThat(recorded.outputPayload()).isEqualTo("{\"ok\":true}");
    }

    /**
     * A thrown exception is read as retryable. It is usually a bug or a transport problem rather than a statement about
     * the input, and failing outright would throw away work a fixed deployment would have finished.
     */
    @Test
    void treatsAThrownExceptionAsWorthAnotherGo() {
        AiJob job = job();
        when(queue.claimBatch(5)).thenReturn(List.of(job));

        workerWith(handlerThat(ignored -> {
            throw new IllegalStateException("boom");
        })).drain();

        AiJobHandler.Outcome recorded = drainAndCapture(job);
        assertThat(recorded.success()).isFalse();
        assertThat(recorded.retryable()).isTrue();
        assertThat(recorded.errorCode()).isEqualTo("AI_JOB_HANDLER_ERROR");
    }

    /** One handler throwing must not stop the rest of the batch being attempted. */
    @Test
    void keepsGoingAfterOneJobThrows() {
        AiJob first = job();
        AiJob second = job();
        when(queue.claimBatch(5)).thenReturn(List.of(first, second));

        workerWith(handlerThat(job -> {
            if (job.getId().equals(first.getId())) {
                throw new IllegalStateException("boom");
            }
            return AiJobHandler.Outcome.succeeded("{}");
        })).drain();

        verify(queue).record(eq(first.getId()), org.mockito.ArgumentMatchers.any());
        verify(queue).record(eq(second.getId()), org.mockito.ArgumentMatchers.any());
    }

    /**
     * No handler is the one failure that is permanent: no amount of retrying will register one, so spending the budget
     * would only delay the same answer.
     */
    @Test
    void failsAJobTypeNothingCanHandleWithoutRetrying() {
        AiJob job = job();
        when(queue.claimBatch(5)).thenReturn(List.of(job));

        workerWith().drain();

        AiJobHandler.Outcome recorded = drainAndCapture(job);
        assertThat(recorded.retryable()).isFalse();
        assertThat(recorded.errorCode()).isEqualTo("AI_JOB_NO_HANDLER");
    }

    @Test
    void doesNothingWhenTheQueueIsEmpty() {
        when(queue.claimBatch(5)).thenReturn(List.of());

        workerWith(handlerThat(ignored -> AiJobHandler.Outcome.succeeded("{}"))).drain();

        verify(queue, org.mockito.Mockito.never()).record(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handsStalledJobsBackUsingTheConfiguredLockTimeout() {
        when(queue.reclaimStalled(Duration.ofMinutes(5))).thenReturn(List.of(job(), job()));

        workerWith().reclaimStalled();

        verify(queue).reclaimStalled(Duration.ofMinutes(5));
    }

    /** A handler that only records calls to onGaveUp, so the tests can say who was told what. */
    private static final class Recording implements AiJobHandler {

        final java.util.List<String> gaveUp = new java.util.ArrayList<>();
        private final java.util.function.Function<AiJob, Outcome> behaviour;

        Recording(java.util.function.Function<AiJob, Outcome> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public AiJobType handles() {
            return AiJobType.SPEECH_ASSESSMENT;
        }

        @Override
        public Outcome run(AiJob job) {
            return behaviour.apply(job);
        }

        @Override
        public void onGaveUp(AiJob job, String errorCode) {
            gaveUp.add(errorCode);
        }
    }

    /**
     * The reason the hook exists. When the queue says this attempt was the last, the job's module is told - otherwise a
     * learner is left waiting on work that has already stopped.
     */
    @Test
    void tellsTheHandlerWhenAJobHasGivenUp() {
        AiJob job = job();
        when(queue.claimBatch(5)).thenReturn(List.of(job));
        when(queue.record(eq(job.getId()), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        Recording handler = new Recording(ignored -> AiJobHandler.Outcome.transientFailure("PROVIDER_DOWN", "x"));

        workerWith(handler).drain();

        assertThat(handler.gaveUp).containsExactly("PROVIDER_DOWN");
    }

    /** While a retry is still coming, nobody is told anything - the answer may yet arrive. */
    @Test
    void saysNothingWhileARetryIsStillComing() {
        AiJob job = job();
        when(queue.claimBatch(5)).thenReturn(List.of(job));
        when(queue.record(eq(job.getId()), org.mockito.ArgumentMatchers.any())).thenReturn(false);
        Recording handler = new Recording(ignored -> AiJobHandler.Outcome.transientFailure("PROVIDER_DOWN", "x"));

        workerWith(handler).drain();

        assertThat(handler.gaveUp).isEmpty();
    }

    /** A handler that throws on its last attempt still gets told, with the code the worker recorded. */
    @Test
    void tellsTheHandlerEvenWhenItsLastAttemptThrew() {
        AiJob job = job();
        when(queue.claimBatch(5)).thenReturn(List.of(job));
        when(queue.record(eq(job.getId()), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        Recording handler = new Recording(ignored -> {
            throw new IllegalStateException("boom");
        });

        workerWith(handler).drain();

        assertThat(handler.gaveUp).containsExactly("AI_JOB_HANDLER_ERROR");
    }

    /**
     * A stall reclaim costs a retry, so it can be the thing that ends a job. The worker tells the module then too - the
     * one path that never passes through a handler's run at all.
     */
    @Test
    void tellsTheHandlerWhenAReclaimUsedTheLastRetry() {
        AiJob finished = job();
        finished.claim(java.time.Instant.now());
        for (int i = 0; i < 3; i++) {
            finished.fail("X", "x", true, java.time.Instant.now());
            if (!finished.finished()) {
                finished.claim(java.time.Instant.now());
            }
        }
        AiJob stillGoing = job();
        when(queue.reclaimStalled(Duration.ofMinutes(5))).thenReturn(List.of(finished, stillGoing));
        Recording handler = new Recording(ignored -> AiJobHandler.Outcome.succeeded("{}"));

        workerWith(handler).reclaimStalled();

        assertThat(handler.gaveUp).containsExactly("AI_JOB_STALLED");
    }

    /** Telling the module happens after the job is recorded; a handler failing at it must not take the batch down. */
    @Test
    void keepsGoingWhenTellingTheHandlerFails() {
        AiJob first = job();
        AiJob second = job();
        when(queue.claimBatch(5)).thenReturn(List.of(first, second));
        when(queue.record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiJobHandler throwingOnGiveUp = new AiJobHandler() {
            @Override
            public AiJobType handles() {
                return AiJobType.SPEECH_ASSESSMENT;
            }

            @Override
            public Outcome run(AiJob job) {
                return Outcome.permanentFailure("BAD", "x");
            }

            @Override
            public void onGaveUp(AiJob job, String errorCode) {
                throw new IllegalStateException("could not write");
            }
        };

        workerWith(throwingOnGiveUp).drain();

        verify(queue).record(eq(second.getId()), org.mockito.ArgumentMatchers.any());
    }
}
