package com.englow3.ai.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.ai.foundation.AiGateway;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobExecutionResult;
import com.englow3.ai.foundation.AiTextResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class PlacementReportJobHandlerTest {
    @Nested
    class Success {

        @Test
        void storesAValidExplanationWithoutChangingTheScore() {
            ObjectMapper objectMapper = new ObjectMapper();
            AiGateway gateway = mock(AiGateway.class);
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            AiJob job = mock(AiJob.class);
            UUID reportId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ObjectNode payload = objectMapper.createObjectNode().put("reportId", reportId.toString())
                    .put("systemPrompt", "Do not change the score").put("userPrompt", "B1 60 percent");
            when(job.getInputPayload()).thenReturn(payload);
            when(job.getRequesterUserId()).thenReturn(userId);
            when(gateway.generate(any(), any(), anyString(), anyString(), eq(true))).thenReturn(new AiTextResult(
                    "{\"summary\":\"B1\",\"strengths\":[\"reading\"],\"learningGaps\":[\"listening\"]}", "model", 10,
                    8));
            PlacementReportJobHandler handler = new PlacementReportJobHandler(gateway, jdbcTemplate, objectMapper);

            AiJobExecutionResult result = handler.execute(job);

            assertThat(result.output().path("summary").asText()).isEqualTo("B1");
            verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
        }

    }

}
