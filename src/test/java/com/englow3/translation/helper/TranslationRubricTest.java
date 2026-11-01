package com.englow3.translation.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class TranslationRubricTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String report(String parts, String errors) {
        return """
                {%s,"summary":"Câu dịch đúng ý.","improved":"I have lived here for three years.","errors":%s}
                """.formatted(parts, errors);
    }

    private static final String FULL_MARKS = "\"meaning\":40,\"grammar\":30,\"vocabulary\":20,\"naturalness\":10";

    @Test
    void addsThePartsItselfAndDerivesTheVerdict() throws Exception {
        JsonNode result = mapper.readTree(TranslationRubric.validate(mapper,
                report("\"meaning\":35,\"grammar\":20,\"vocabulary\":15,\"naturalness\":7", """
                        [{"type":"GRAMMAR","original":"I live here","correction":"I have lived here",
                          "explanation":"Dùng hiện tại hoàn thành cho hành động kéo dài tới hiện tại."}]
                        """)));

        assertThat(result.path("score").asInt()).isEqualTo(77);
        assertThat(result.path("verdict").asText()).isEqualTo("GOOD");
        assertThat(result.path("parts").path("grammar").asInt()).isEqualTo(20);
        assertThat(result.path("errors")).hasSize(1);
        assertThat(result.path("rubricVersion").asText()).isEqualTo(TranslationRubric.VERSION);
    }

    /** A total the model states is ignored: only the parts count, and they are added here. */
    @Test
    void ignoresAnyTotalTheModelSupplies() throws Exception {
        JsonNode result = mapper.readTree(TranslationRubric.validate(mapper,
                report(FULL_MARKS + ",\"score\":12,\"verdict\":\"NEEDS_WORK\"", "[]")));

        assertThat(result.path("score").asInt()).isEqualTo(100);
        assertThat(result.path("verdict").asText()).isEqualTo("EXCELLENT");
    }

    @ParameterizedTest
    @CsvSource({ "100,EXCELLENT", "85,EXCELLENT", "84,GOOD", "65,GOOD", "64,PARTIAL", "40,PARTIAL", "39,NEEDS_WORK",
            "0,NEEDS_WORK" })
    void bandsTheTotal(int score, String verdict) {
        assertThat(TranslationRubric.verdict(score)).isEqualTo(verdict);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"meaning\":41,\"grammar\":30,\"vocabulary\":20,\"naturalness\":10",
            "\"meaning\":-1,\"grammar\":30,\"vocabulary\":20,\"naturalness\":10",
            "\"meaning\":40,\"grammar\":30,\"vocabulary\":20",
            "\"meaning\":39.5,\"grammar\":30,\"vocabulary\":20,\"naturalness\":10",
            "\"meaning\":\"40\",\"grammar\":30,\"vocabulary\":20,\"naturalness\":10",
            "\"meaning\":true,\"grammar\":30,\"vocabulary\":20,\"naturalness\":10" })
    void refusesAPartThatIsMissingOrOutOfRange(String parts) {
        assertThatThrownBy(() -> TranslationRubric.validate(mapper, report(parts, "[]")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listsAMistakeTheModelRepeatedOnlyOnce() throws Exception {
        String twice = """
                {"type":"GRAMMAR","original":"I live here","correction":"I have lived here","explanation":"Thì."}
                """;
        JsonNode result = mapper.readTree(TranslationRubric.validate(mapper, report(
                "\"meaning\":20,\"grammar\":10,\"vocabulary\":10,\"naturalness\":2", "[" + twice + "," + twice + "]")));

        assertThat(result.path("errors")).hasSize(1);
    }

    @Test
    void refusesAnErrorOfAnUnknownType() {
        assertThatThrownBy(() -> TranslationRubric.validate(mapper, report(FULL_MARKS, """
                [{"type":"STYLE","original":"a","correction":"b","explanation":"c"}]
                """))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAReportWithoutFeedbackText() {
        assertThatThrownBy(() -> TranslationRubric.validate(mapper,
                "{" + FULL_MARKS + ",\"summary\":\" \",\"improved\":\"x\",\"errors\":[]}"))
                        .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "not json", "[]", "```json\n{}\n```" })
    void refusesSomethingThatIsNotAReport(String raw) {
        assertThatThrownBy(() -> TranslationRubric.validate(mapper, raw)).isInstanceOf(BadRequestException.class);
    }
}
