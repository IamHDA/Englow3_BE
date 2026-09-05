package com.englow3.ai.learningpath;

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

class LearningPathExplanationJobHandlerTest {
    @Nested
    class Success {

        @Test
        void savesExplanationWithoutChangingOrderedItems() {
            ObjectMapper objectMapper = new ObjectMapper();
            AiGateway gateway = mock(AiGateway.class);
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            AiJob job = mock(AiJob.class);
            UUID pathId = UUID.randomUUID();
            ObjectNode payload = objectMapper.createObjectNode().put("pathId", pathId.toString())
                    .put("systemPrompt", "Keep order").put("userPrompt", "concept-a, concept-b");
            when(job.getInputPayload()).thenReturn(payload);
            when(job.getRequesterUserId()).thenReturn(UUID.randomUUID());
            when(gateway.generate(any(), any(), anyString(), anyString(), eq(true))).thenReturn(new AiTextResult(
                    "{\"explanation\":\"Start with the prerequisite.\",\"weeklyAdvice\":[\"Practice daily\"]}", "model",
                    11, 9));
            LearningPathExplanationJobHandler handler = new LearningPathExplanationJobHandler(gateway, jdbcTemplate,
                    objectMapper);

            AiJobExecutionResult result = handler.execute(job);

            assertThat(result.output().path("explanation").asText()).contains("prerequisite");
            verify(jdbcTemplate).update("update learning_paths set explanation = ? where id = ?",
                    "Start with the prerequisite.", pathId);
        }

    }

}
