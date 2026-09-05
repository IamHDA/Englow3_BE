package com.englow3.ai.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class WritingAssessmentJobHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode criteria() throws Exception {
        return objectMapper.readTree("""
                [
                  {"name":"Grammar","weight":0.6},
                  {"name":"Vocabulary","weight":0.4}
                ]
                """);
    }

    private JsonNode validResult() throws Exception {
        return objectMapper.readTree("""
                {
                  "criterionScores": [
                    {
                      "criterion": "Grammar",
                      "score": 80,
                      "feedback": "The sentence is grammatically complete.",
                      "evidence": ["I enjoy learning English"]
                    },
                    {
                      "criterion": "Vocabulary",
                      "score": 50,
                      "feedback": "Vocabulary is clear but limited.",
                      "evidence": ["helps my career"]
                    }
                  ],
                  "cefrLevel": "B1",
                  "summary": "A clear response with room for more precise vocabulary.",
                  "strengths": ["Clear main idea"],
                  "improvements": ["Use more specific vocabulary"],
                  "correctedResponse": "I enjoy learning English because it helps my career.",
                  "sampleRevision": "I enjoy studying English because stronger communication skills advance my career."
                }
                """);
    }

    private void assertSchemaError(JsonNode result, JsonNode rubric, String response) {
        assertThatThrownBy(() -> WritingAssessmentJobHandler.validate(result, rubric, response))
                .isInstanceOf(AiProviderException.class).extracting("code").isEqualTo("AI_WRITING_SCHEMA_INVALID");
    }

    @Nested
    class Success {

        @Test
        void computesOverallScoreFromServerSideRubricWeights() throws Exception {
            WritingAssessmentJobHandler.ValidatedAssessment result = WritingAssessmentJobHandler.validate(validResult(),
                    criteria(), "I enjoy learning English because it helps my career.");

            assertThat(result.overallScore()).isEqualByComparingTo(new BigDecimal("68.00"));
            assertThat(result.cefrLevel()).isEqualTo("B1");
            assertThat(result.criterionScores()).hasSize(2);
        }

        @Test
        void rejectsFabricatedEvidence() throws Exception {
            JsonNode result = validResult();
            ((com.fasterxml.jackson.databind.node.ArrayNode) result.path("criterionScores").get(0).path("evidence"))
                    .set(0, objectMapper.getNodeFactory().textNode("This sentence was never submitted"));

            assertSchemaError(result, criteria(), "I enjoy learning English because it helps my career.");
        }

        @Test
        void rejectsMissingRubricCriterion() throws Exception {
            JsonNode result = validResult();
            ((com.fasterxml.jackson.databind.node.ArrayNode) result.path("criterionScores")).remove(1);

            assertSchemaError(result, criteria(), "I enjoy learning English because it helps my career.");
        }

        @Test
        void rejectsUnknownRubricCriterion() throws Exception {
            JsonNode result = validResult();
            ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("criterionScores").get(1)).put("criterion",
                    "Creativity");

            assertSchemaError(result, criteria(), "I enjoy learning English because it helps my career.");
        }

        @Test
        void rejectsOutOfRangeScore() throws Exception {
            JsonNode result = validResult();
            ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("criterionScores").get(0)).put("score", 101);

            assertSchemaError(result, criteria(), "I enjoy learning English because it helps my career.");
        }

        @Test
        void rejectsUnsupportedCefrLevel() throws Exception {
            JsonNode result = validResult();
            ((com.fasterxml.jackson.databind.node.ObjectNode) result).put("cefrLevel", "C2");

            assertSchemaError(result, criteria(), "I enjoy learning English because it helps my career.");
        }

        @Test
        void rejectsEmptyImprovementList() throws Exception {
            JsonNode result = validResult();
            ((com.fasterxml.jackson.databind.node.ArrayNode) result.path("improvements")).removeAll();

            assertSchemaError(result, criteria(), "I enjoy learning English because it helps my career.");
        }

    }

}
