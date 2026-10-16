package com.englow3.ai.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.service.AiJobWorkerQueue;

class AiJobWorkerTest {
    @Test
    void sleepingAdapterDefersWithoutCallingProviderOrSpendingARetry() {
        AiJob claimed = job(AiJobType.TUTOR_REPLY);
        when(queue.claimBatch(eq(1), eq(List.of("TUTOR_REPLY")), any())).thenReturn(List.of(claimed));
        when(queue.claimBatch(eq(1), eq(List.of("PRODUCTIVE_ASSESSMENT", "SPEECH_ASSESSMENT")), any()))
                .thenReturn(List.of());
        var readiness = mock(com.englow3.ai.client.AiServiceReadiness.class);
        var handler = mock(AiJobHandler.class);
        when(handler.handles()).thenReturn("TUTOR_REPLY");
        try (var worker = new AiJobWorker(queue, List.of(handler), Duration.ofMinutes(3), Duration.ofSeconds(140),
                readiness)) {
            worker.drain();
        }
        verify(queue).defer(claimed.getId(), claimed.getStartedAt(), Duration.ofSeconds(10));
        verify(handler, never()).run(any(), any(), any());
        verify(queue, never()).record(any(), any(), any(), any());
    }

    private final AiJobWorkerQueue queue = mock(AiJobWorkerQueue.class);

    private AiJob job(AiJobType type) {
        AiJob job = AiJob.pending(type, "TARGET", UUID.randomUUID(), "provider", "model", "v1", "{}",
                UUID.randomUUID().toString(), (short) 3);
        job.claim(Instant.now());
        return job;
    }

    private AiJobHandler handler(String type, Runnable action) {
        return new AiJobHandler() {
            public String handles() {
                return type;
            }

            public Outcome run(UUID id, UUID target, String input) {
                action.run();
                return Outcome.succeeded("{}");
            }
        };
    }

    @Test
    void slowSpeakingDoesNotBlockTutorAndDoesNotPreclaimMoreWork() throws Exception {
        AiJob speaking = job(AiJobType.SPEECH_ASSESSMENT), tutor = job(AiJobType.TUTOR_REPLY);
        when(queue.claimBatch(eq(1), eq(List.of("TUTOR_REPLY")), any())).thenReturn(List.of(tutor));
        when(queue.claimBatch(eq(1), eq(List.of("PRODUCTIVE_ASSESSMENT", "SPEECH_ASSESSMENT")), any()))
                .thenReturn(List.of(speaking));
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        Runnable block = () -> {
            entered.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try (var worker = worker(List.of(handler("TUTOR_REPLY", block), handler("SPEECH_ASSESSMENT", block)))) {
            worker.drain();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            worker.drain();
            verify(queue, times(2)).claimBatch(eq(1), anyList(), any());
            release.countDown();
        }
        verify(queue).record(eq(tutor.getId()), eq(tutor.getStartedAt()), any(), any());
        verify(queue).record(eq(speaking.getId()), eq(speaking.getStartedAt()), any(), any());
    }

    @Test
    void handlerExceptionIsRecordedAndNotifiesOnlyAfterTerminalFailure() {
        AiJob job = job(AiJobType.TUTOR_REPLY);
        when(queue.claimBatch(eq(1), eq(List.of("TUTOR_REPLY")), any())).thenReturn(List.of(job));
        when(queue.claimBatch(eq(1), eq(List.of("PRODUCTIVE_ASSESSMENT", "SPEECH_ASSESSMENT")), any()))
                .thenReturn(List.of());
        when(queue.record(eq(job.getId()), any(), any(), any())).thenReturn(true);
        AiJobHandler handler = mock(AiJobHandler.class);
        when(handler.handles()).thenReturn("TUTOR_REPLY");
        when(handler.run(any(), any(), any())).thenThrow(new IllegalStateException("private"));
        try (var worker = worker(List.of(handler))) {
            worker.drain();
        }
        verify(handler).onGaveUp(job.getTargetId(), "AI_JOB_HANDLER_ERROR");
        verify(handler, never()).complete(any(), any(), any());
    }

    @Test
    void expiredComputationDoesNotPublishItsResult() {
        AiJob job = job(AiJobType.TUTOR_REPLY);
        when(queue.claimBatch(eq(1), eq(List.of("TUTOR_REPLY")), any())).thenReturn(List.of(job));
        when(queue.claimBatch(eq(1), eq(List.of("PRODUCTIVE_ASSESSMENT", "SPEECH_ASSESSMENT")), any()))
                .thenReturn(List.of());
        try (var worker = new AiJobWorker(queue, List.of(handler("TUTOR_REPLY", () -> {
        })), Duration.ofMinutes(3), Duration.ZERO)) {
            worker.drain();
        }
        verify(queue).record(eq(job.getId()), any(),
                argThat(result -> !result.success() && result.errorCode().equals("AI_JOB_DEADLINE")), any());
    }

    private AiJobWorker worker(List<AiJobHandler> handlers) {
        return new AiJobWorker(queue, handlers, Duration.ofMinutes(3), Duration.ofSeconds(140));
    }
}
