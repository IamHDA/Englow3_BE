package com.englow3.ai.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class TutorReplyContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsOnlyCitationsFromTheRetrievedSet() throws Exception {
        Map<String, JsonNode> allowed = TutorReplyJobHandler.references(references());

        assertThat(TutorReplyJobHandler.selectedReferences(objectMapper.readTree("[\"FLASHCARD:word:R2\"]"), allowed))
                .hasSize(1);
        assertThatThrownBy(() -> TutorReplyJobHandler
                .selectedReferences(objectMapper.readTree("[\"FLASHCARD:unknown:R1\"]"), allowed))
                        .isInstanceOf(AiProviderException.class).extracting("code")
                        .isEqualTo("AI_TUTOR_CITATION_INVALID");
    }

    @Test
    void rejectsDuplicateCitationIds() throws Exception {
        Map<String, JsonNode> allowed = TutorReplyJobHandler.references(references());

        assertThatThrownBy(() -> TutorReplyJobHandler
                .selectedReferences(objectMapper.readTree("[\"FLASHCARD:word:R2\",\"FLASHCARD:word:R2\"]"), allowed))
                        .isInstanceOf(AiProviderException.class).extracting("code")
                        .isEqualTo("AI_TUTOR_CITATION_INVALID");
    }

    @Test
    void enforcesModeSpecificOutput() throws Exception {
        JsonNode missingCorrection = objectMapper.readTree("{\"answer\":\"Try again\"}");
        JsonNode writingFeedback = objectMapper
                .readTree("{\"answer\":\"Good\",\"feedbackItems\":[\"Use a clearer topic sentence\"]}");

        assertThatThrownBy(
                () -> TutorReplyJobHandler.validateModeOutput(TutorMode.SENTENCE_CORRECTION, missingCorrection))
                        .isInstanceOf(AiProviderException.class).extracting("code")
                        .isEqualTo("AI_TUTOR_SCHEMA_INVALID");
        TutorReplyJobHandler.validateModeOutput(TutorMode.WRITING_FEEDBACK, writingFeedback);
    }

    private JsonNode references() throws Exception {
        return objectMapper.readTree("""
                [{
                  "referenceId":"FLASHCARD:word:R2",
                  "contentType":"FLASHCARD",
                  "contentId":"word",
                  "revision":2,
                  "label":"word",
                  "groundingHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }]
                """);
    }
}
