package com.englow3.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.englow3.ai.foundation.AiProviderException;
import com.englow3.ai.foundation.AiTextResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ContentDraftJobHandlerTest {
    private final AiGateway gateway = mock(AiGateway.class);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ContentDraftJobHandler handler = new ContentDraftJobHandler(gateway, jdbcTemplate, objectMapper,

            new AiContentValidator(objectMapper));

    private AiJob job(UUID draftId) {
        AiJob job = mock(AiJob.class);
        ObjectNode payload = objectMapper.createObjectNode().put("draftId", draftId.toString())
                .put("contentType", "QUIZ").put("level", "B1").put("systemPrompt", "system").put("userPrompt", "user");
        when(job.getInputPayload()).thenReturn(payload);
        when(job.getRequesterUserId()).thenReturn(UUID.randomUUID());
        return job;
    }

    @Nested
    class Success {

        @Test
        void storesValidGeneratedDraftAsEditableDraft() {
            UUID draftId = UUID.randomUUID();
            AiJob job = job(draftId);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("gram_present_simple"))).thenReturn(1);
            when(gateway.generate(any(), any(), eq("system"), eq("user"), eq(true))).thenReturn(new AiTextResult("""
                    {
                      "title":"Present simple quiz",
                      "items":[{
                        "question":"Which sentence is correct?",
                        "options":[
                          {"text":"She works here.","isCorrect":true,"rationaleVi":"Chia động từ đúng."},
                          {"text":"She work here.","isCorrect":false,"rationaleVi":"Thiếu -s."},
                          {"text":"She working here.","isCorrect":false,"rationaleVi":"Thiếu trợ động từ."}
                        ],
                        "explanationEn":"Third-person singular takes -s.",
                        "explanationVi":"Ngôi thứ ba số ít thêm -s.",
                        "difficultyPrior":0.2,
                        "conceptIds":["gram_present_simple"]
                      }]
                    }
                    """, "model", 10, 8));

            AiJobExecutionResult result = handler.execute(job);

            assertThat(result.inputTokens()).isEqualTo(10);
            assertThat(result.output().path("status").asText()).isEqualTo("DRAFT");
            verify(jdbcTemplate).update(any(String.class), any(String.class), any(String.class), any(String.class),
                    eq(draftId));
        }

    }

    @Nested
    class Failure {

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

    }

}
