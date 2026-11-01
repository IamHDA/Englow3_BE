package com.englow3.translation.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.ai.api.AiJobHandler.Outcome;
import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;
import com.englow3.translation.service.TranslationGradeWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

class TranslationAssessmentHandlerTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final TranslationGradeWriter writer = mock(TranslationGradeWriter.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TranslationAssessmentHandler handler = new TranslationAssessmentHandler(llm, writer, mapper);
    private final UUID attemptId = UUID.randomUUID();

    private String payload(UUID id) {
        return """
                {"attemptId":"%s","sourceVi":"Trời đang mưa.","referenceEn":"It is raining.",
                 "learnerTranslation":"It raining."}
                """.formatted(id);
    }

    /** What ai_service answers: the model's text inside "content". */
    private static String adapterAnswer(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of("content", content));
    }

    @Test
    void gradesAndHandsBackTheValidatedReport() throws Exception {
        when(llm.generateStructured(anyString(), anyString(), anyInt())).thenReturn(adapterAnswer("""
                {"meaning":38,"grammar":20,"vocabulary":20,"naturalness":7,"summary":"Thiếu động từ to be.",
                 "improved":"It is raining.","errors":[{"type":"GRAMMAR","original":"It raining",
                 "correction":"It is raining","explanation":"Cần 'is' ở thì hiện tại tiếp diễn."}]}
                """));

        Outcome outcome = handler.run(UUID.randomUUID(), attemptId, payload(attemptId));

        assertThat(outcome.success()).isTrue();
        assertThat(mapper.readTree(outcome.outputPayload()).path("score").asInt()).isEqualTo(85);
        // The grader sees the sentences, not this system's ids.
        verify(llm).generateStructured(anyString(), argThat(prompt -> !prompt.contains(attemptId.toString())
                && prompt.contains("It raining.") && prompt.contains("Trời đang mưa.")), anyInt());
    }

    @Test
    void completionWritesTheGrade() {
        handler.complete(attemptId, payload(attemptId), "{\"score\":85}");
        verify(writer).storeGrade(eq(attemptId), eq("{\"score\":85}"));
    }

    @Test
    void refusesAPayloadForAnotherAttempt() {
        Outcome outcome = handler.run(UUID.randomUUID(), attemptId, payload(UUID.randomUUID()));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isFalse();
        verifyNoInteractions(llm);
    }

    /** A malformed report is usually a one-off; the next sample of the same model is worth asking for. */
    @Test
    void retriesAnUnusableReport() throws Exception {
        when(llm.generateStructured(anyString(), anyString(), anyInt())).thenReturn(adapterAnswer("{\"meaning\":99}"));

        Outcome outcome = handler.run(UUID.randomUUID(), attemptId, payload(attemptId));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.errorCode()).isEqualTo("TRANSLATION_REPORT_INVALID");
    }

    @Test
    void passesOnWhetherAProviderFailureIsWorthRetrying() {
        when(llm.generateStructured(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("AI_TIMEOUT", "slow", true))
                .thenThrow(new LlmException("AI_REJECTED", "no", false));

        assertThat(handler.run(UUID.randomUUID(), attemptId, payload(attemptId)).retryable()).isTrue();
        assertThat(handler.run(UUID.randomUUID(), attemptId, payload(attemptId)).retryable()).isFalse();
    }

    @Test
    void givingUpMarksTheAttemptFailed() {
        handler.onGaveUp(attemptId, "AI_TIMEOUT");
        verify(writer).markFailed(attemptId, "AI_TIMEOUT");
        verify(writer, never()).storeGrade(eq(attemptId), anyString());
    }
}
