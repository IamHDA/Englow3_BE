package com.englow3.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.ai.foundation.AiGateway;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobExecutionResult;
import com.englow3.ai.foundation.AiProviderException;
import com.englow3.ai.foundation.AiTextResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ContentDraftJobHandlerTest {

    private final AiGateway gateway = mock(AiGateway.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContentDraftJobHandler handler = new ContentDraftJobHandler(gateway, jdbcTemplate, objectMapper);

    @Test
    void storesValidGeneratedDraftAsEditableDraft() {
        UUID draftId = UUID.randomUUID();
        AiJob job = job(draftId);
        when(gateway.generate(any(), any(), eq("system"), eq("user"), eq(true)))
                .thenReturn(new AiTextResult("{\"title\":\"T\",\"items\":[{\"question\":\"Q\"}]}", "model", 10, 8));

        AiJobExecutionResult result = handler.execute(job);

        assertThat(result.inputTokens()).isEqualTo(10);
        assertThat(result.output().path("status").asText()).isEqualTo("DRAFT");
        verify(jdbcTemplate).update(any(String.class), any(String.class), eq(draftId));
    }

    @Test
    void rejectsGeneratedContentWithoutItems() {
        UUID draftId = UUID.randomUUID();
        AiJob job = job(draftId);
        when(gateway.generate(any(), any(), any(), any(), eq(true)))
                .thenReturn(new AiTextResult("{\"title\":\"empty\"}", "model", 1, 1));

        assertThatThrownBy(() -> handler.execute(job)).isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).code())
                .isEqualTo("AI_CONTENT_SCHEMA_INVALID");
    }

    private AiJob job(UUID draftId) {
        AiJob job = mock(AiJob.class);
        ObjectNode payload = objectMapper.createObjectNode().put("draftId", draftId.toString())
                .put("systemPrompt", "system").put("userPrompt", "user");
        when(job.getInputPayload()).thenReturn(payload);
        when(job.getRequesterUserId()).thenReturn(UUID.randomUUID());
        return job;
    }
}
