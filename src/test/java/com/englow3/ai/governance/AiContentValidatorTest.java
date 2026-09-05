package com.englow3.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiContentValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiContentValidator validator = new AiContentValidator(objectMapper);

    private JsonNode quiz() throws Exception {
        return objectMapper.readTree("""
                {"title":"Present simple quiz","items":[{
                  "question":"Which sentence is correct?",
                  "options":[
                    {"text":"She works here.","isCorrect":true,"rationaleVi":"Chia động từ đúng."},
                    {"text":"She work here.","isCorrect":false,"rationaleVi":"Thiếu -s."},
                    {"text":"She working here.","isCorrect":false,"rationaleVi":"Thiếu trợ động từ."}
                  ],
                  "explanationEn":"Third-person singular takes -s.",
                  "explanationVi":"Ngôi thứ ba số ít thêm -s.",
                  "difficultyPrior":0.2,"conceptIds":["gram_present_simple"]
                }]}
                """);
    }

    private void assertInvalid(AiGovernanceDtos.ContentType type, JsonNode content, String message) {
        assertThatThrownBy(() -> validator.validate(type, "B1", content)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining(message);
    }

    @Nested
    class Success {

        @Test
        void validatesQuizAndProducesStableSha256Metadata() throws Exception {
            AiContentValidator.ValidationResult result = validator.validate(AiGovernanceDtos.ContentType.QUIZ, "B1",
                    quiz());

            assertThat(result.itemCount()).isEqualTo(1);
            assertThat(result.contentHash()).matches("[0-9a-f]{64}");
            assertThat(result.conceptIds()).containsExactly("gram_present_simple");
            assertThat(result.report().path("valid").asBoolean()).isTrue();
        }

        @Test
        void rejectsQuizWithMoreThanOneCorrectAnswer() throws Exception {
            JsonNode content = quiz();
            ((com.fasterxml.jackson.databind.node.ObjectNode) content.path("items").get(0).path("options").get(1))
                    .put("isCorrect", true);

            assertInvalid(AiGovernanceDtos.ContentType.QUIZ, content, "exactly one correct");
        }

        @Test
        void validatesDictationWithOrderedSegmentsThatReproduceScript() throws Exception {
            JsonNode content = objectMapper.readTree("""
                    {"title":"Commute","items":[{
                      "script":"I take the bus to work.","accent":"US","difficultyPrior":0.3,
                      "segments":[
                        {"text":"I take the bus","startMs":0,"endMs":1000},
                        {"text":"to work.","startMs":1000,"endMs":1600}
                      ],
                      "conceptIds":["listen_daily_transport"]
                    }]}
                    """);

            assertThat(validator.validate(AiGovernanceDtos.ContentType.DICTATION, "A2", content).itemCount()).isOne();
        }

        @Test
        void rejectsDictationWhoseSegmentsDoNotReproduceTheScript() throws Exception {
            JsonNode content = objectMapper.readTree("""
                    {"title":"Commute","items":[{
                      "script":"I take the bus to work.","accent":"US","difficultyPrior":0.3,
                      "segments":[{"text":"Different words","startMs":0,"endMs":1000}],
                      "conceptIds":["listen_daily_transport"]
                    }]}
                    """);

            assertInvalid(AiGovernanceDtos.ContentType.DICTATION, content, "reproduce the full script");
        }

        @Test
        void validatesFlashcardContract() throws Exception {
            JsonNode content = objectMapper.readTree("""
                    {"title":"Travel words","items":[{
                      "lemma":"itinerary","pos":"noun","senseLabelEn":"travel plan","ipaUs":"/aɪˈtɪnəreri/",
                      "definitionEn":"A planned route or journey.","definitionVi":"Lịch trình chuyến đi.",
                      "examples":[{"sentence":"Please send the itinerary.","translation":"Vui lòng gửi lịch trình."}],
                      "topics":["travel"],"difficultyPrior":0.5,"conceptIds":["vocab_travel_planning"]
                    }]}
                    """);

            assertThat(validator.validate(AiGovernanceDtos.ContentType.FLASHCARDS, "B1", content).itemCount()).isOne();
        }

        @Test
        void rejectsDuplicateGrammarItemsAfterNormalization() throws Exception {
            JsonNode content = objectMapper.readTree("""
                    {"title":"Grammar","items":[
                      {"titleEn":"Present Perfect","titleVi":"Hiện tại hoàn thành","theoryVi":"Lý thuyết.",
                       "theoryEnSummary":"Theory.","formPatterns":["have + V3"],"difficultyPrior":0.4,
                       "conceptIds":["gram_present_perfect"]},
                      {"titleEn":" present   perfect ","titleVi":"Hiện tại hoàn thành","theoryVi":"Lý thuyết.",
                       "theoryEnSummary":"Theory.","formPatterns":["have + V3"],"difficultyPrior":0.4,
                       "conceptIds":["gram_present_perfect"]}
                    ]}
                    """);

            assertInvalid(AiGovernanceDtos.ContentType.GRAMMAR_LESSON, content, "duplicate items");
        }

    }

}
