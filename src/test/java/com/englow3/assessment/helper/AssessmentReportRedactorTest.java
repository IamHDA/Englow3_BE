package com.englow3.assessment.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssessmentReportRedactorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String REPORT = """
            {"overall":6.5,"estimated":true,"rubricVersion":"productive-v1",
             "criteria":[{"key":"TASK_RESPONSE","score":7,"feedback":"Answers every part","quote":"I believe"},
                         {"key":"COHERENCE_COHESION","score":5.5,"feedback":"Links are repetitive"},
                         {"key":"LEXICAL_RESOURCE","score":5.5,"feedback":"Safe vocabulary"},
                         {"key":"GRAMMATICAL_RANGE","score":7,"feedback":"Mostly accurate"}],
             "summary":"A clear answer","strengths":["Clear position"],"improvements":["Vary linking words"]}
            """;

    private JsonNode redacted(String report) throws Exception {
        return mapper.readTree(AssessmentReportRedactor.forLearner(mapper, report));
    }

    @Test
    void removesEveryCriterionScoreButKeepsTheOverallAndTheWords() throws Exception {
        JsonNode r = redacted(REPORT);

        for (JsonNode criterion : r.path("criteria")) {
            assertFalse(criterion.has("score"));
            assertTrue(criterion.path("feedback").isTextual());
        }
        assertEquals("I believe", r.path("criteria").get(0).path("quote").asText());
        assertEquals(6.5, r.path("overall").asDouble());
        assertEquals("A clear answer", r.path("summary").asText());
        assertEquals("Clear position", r.path("strengths").get(0).asText());
        assertEquals("Vary linking words", r.path("improvements").get(0).asText());
        assertTrue(r.path("scoresHidden").asBoolean());
        assertFalse(AssessmentReportRedactor.forLearner(mapper, REPORT).contains("5.5"));
    }

    /** Two criteria share the lowest band; the earlier one in the rubric is the one to practise first. */
    @Test
    void namesTheWeakestCriterionAndKeepsTheRubricOrderOnATie() throws Exception {
        assertEquals("COHERENCE_COHESION", redacted(REPORT).path("focusCriterion").asText());
    }

    @Test
    void leavesAReportItCannotReadAsItIs() {
        assertNull(AssessmentReportRedactor.forLearner(mapper, null));
        assertEquals("not json", AssessmentReportRedactor.forLearner(mapper, "not json"));
        assertEquals("[]", AssessmentReportRedactor.forLearner(mapper, "[]"));
        assertEquals("{\"overall\":3}", AssessmentReportRedactor.forLearner(mapper, "{\"overall\":3}"));
    }
}
