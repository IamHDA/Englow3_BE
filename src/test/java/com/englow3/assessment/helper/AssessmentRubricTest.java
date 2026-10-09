package com.englow3.assessment.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssessmentRubricTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsEmptyAndNullProviderReports() {
        for (String content : List.of("", "null", "[]")) {
            assertThrows(BadRequestException.class,
                    () -> AssessmentRubric.validate(mapper, AssessmentSkill.WRITING, content));
        }
    }

    static String report(String first, double score) {
        return """
                {"overall":9,"criteria":[{"key":"%s","score":%s,"feedback":"Specific evidence"},
                {"key":"COHERENCE_COHESION","score":6,"feedback":"Organization"},
                {"key":"LEXICAL_RESOURCE","score":6,"feedback":"Vocabulary"},
                {"key":"GRAMMATICAL_RANGE","score":6,"feedback":"Grammar"}],
                "summary":"Practice feedback","strengths":["Clear idea"],"improvements":["Develop evidence"]}
                """.formatted(first, score);
    }

    @Test
    void computesOverallInsteadOfTrustingModel() throws Exception {
        var r = mapper.readTree(AssessmentRubric.validate(mapper, AssessmentSkill.WRITING, report("TASK_RESPONSE", 7)));
        assertEquals(6.5, r.path("overall").asDouble());
        assertTrue(r.path("estimated").asBoolean());
    }

    @Test
    void rejectsMissingCriterion() {
        assertThrows(BadRequestException.class,
                () -> AssessmentRubric.validate(mapper, AssessmentSkill.WRITING, report("PRONUNCIATION", 7)));
    }

    @Test
    void rejectsWrongSkillRubric() {
        assertThrows(BadRequestException.class,
                () -> AssessmentRubric.validate(mapper, AssessmentSkill.SPEAKING, report("TASK_RESPONSE", 7)));
    }

    @Test
    void rejectsDuplicateCriteria() {
        assertThrows(BadRequestException.class,
                () -> AssessmentRubric.validate(mapper, AssessmentSkill.WRITING, report("LEXICAL_RESOURCE", 7)));
    }

    @Test
    void rejectsFractionOutsideHalfBands() {
        assertThrows(BadRequestException.class,
                () -> AssessmentRubric.validate(mapper, AssessmentSkill.WRITING, report("TASK_RESPONSE", 6.2)));
    }

    @Test
    void rejectsOutOfRange() {
        assertThrows(BadRequestException.class,
                () -> AssessmentRubric.validate(mapper, AssessmentSkill.WRITING, report("TASK_RESPONSE", 10)));
    }

    @Test
    void rejectsMissingFeedback() {
        assertThrows(BadRequestException.class, () -> AssessmentRubric.validate(mapper, AssessmentSkill.WRITING,
                report("TASK_RESPONSE", 7).replace("Specific evidence", "")));
    }

    @Test
    void normalizesWordCount() {
        assertEquals(3, AssessmentRubric.wordCount(" one  two\nthree "));
        assertEquals(0, AssessmentRubric.wordCount(" "));
    }
}
