package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class AiJobTest {

    @Test
    void completesAClaimedJob() {
        AiJob job = pending();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");

        job.claim("worker-1", now);
        job.succeed(JsonNodeFactory.instance.objectNode().put("answer", "ok"), 12, 7, now.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(AiJobStatus.SUCCEEDED);
        assertThat(job.getInputTokens()).isEqualTo(12);
        assertThat(job.getOutputTokens()).isEqualTo(7);
        assertThat(job.getLockedBy()).isNull();
    }

    @Test
    void retriesTransientFailuresThenStops() {
        AiJob job = pending();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");

        job.claim("worker-1", now);
        job.fail("AI_PROVIDER_UNAVAILABLE", "Provider unavailable", true, now);

        assertThat(job.getStatus()).isEqualTo(AiJobStatus.RETRY_SCHEDULED);
        assertThat(job.getAvailableAt()).isAfter(now);
        assertThat(job.getRetryCount()).isEqualTo((short) 1);
    }

    @Test
    void doesNotCancelAProcessingJob() {
        AiJob job = pending();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        job.claim("worker-1", now);

        job.cancel(now.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(AiJobStatus.PROCESSING);
    }

    private AiJob pending() {
        return AiJob.pending(UUID.randomUUID(), AiCapability.TUTOR, "TUTOR_REPLY", "CONVERSATION", UUID.randomUUID(),
                "openai-compatible", "test-model", "1", JsonNodeFactory.instance.objectNode().put("message", "hello"),
                "request-1", "trace-1");
    }
}
