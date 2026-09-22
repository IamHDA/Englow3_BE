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
        when(queue.reclaimStalled(Duration.ofMinutes(5))).thenReturn(2);

        workerWith().reclaimStalled();

        verify(queue).reclaimStalled(Duration.ofMinutes(5));
    }
}
