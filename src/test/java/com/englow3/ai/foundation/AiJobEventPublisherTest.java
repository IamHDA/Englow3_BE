package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class AiJobEventPublisherTest {
    @Nested
    class Success {

        @Test
        void terminalPayloadContainsOnlyOperationalIdentifiers() {
            var input = JsonNodeFactory.instance.objectNode().put("learnerText", "private transcript")
                    .put("systemPrompt", "private prompt");
            AiJob job = AiJob.pending(UUID.randomUUID(), AiCapability.SPEAKING, "SPEAKING_ASSESSMENT",
                    "SPEAKING_SESSION", UUID.randomUUID(), "ai-service", "speech-model", "2", input, "request-1",
                    "trace-1");

            var payload = AiJobEventPublisher.safePayload(new ObjectMapper(), job, "SUCCEEDED");

            assertThat(payload.fieldNames()).toIterable().containsExactlyInAnyOrder("jobId", "status", "capability",
                    "targetType", "targetId");
            assertThat(payload.toString()).doesNotContain("private transcript", "private prompt", "learnerText",
                    "systemPrompt");
        }

    }

}
