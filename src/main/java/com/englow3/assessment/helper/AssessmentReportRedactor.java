package com.englow3.assessment.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * What a learner is allowed to read of a grading report: the overall band and the written feedback, but not the band of
 * each criterion.
 * <p>
 * Done on the server because the report travels as one JSON string - hiding a number in the screen would leave it in
 * the response for anyone who opens the network tab. The stored report is untouched; only the copy sent out loses the
 * scores, so reports graded before this rule existed are covered too.
 */
public final class AssessmentReportRedactor {

    private AssessmentReportRedactor() {
    }

    /**
     * The report without per-criterion scores. {@code focusCriterion} stands in for them where the screen still has to
     * point at the weakest area, so "practise this next" works without the learner being told the numbers behind it. A
     * report that cannot be read is returned as it is: it has no scores to protect and nothing better to show.
     */
    public static String forLearner(ObjectMapper mapper, String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            return reportJson;
        }
        try {
            JsonNode parsed = mapper.readTree(reportJson);
            if (!(parsed instanceof ObjectNode report) || !(report.get("criteria") instanceof ArrayNode criteria)) {
                return reportJson;
            }
            String focus = null;
            double lowest = Double.MAX_VALUE;
            for (JsonNode criterion : criteria) {
                // Strictly lower, so a tie keeps the criterion that comes first in the rubric.
                if (criterion.path("score").isNumber() && criterion.path("score").asDouble() < lowest) {
                    lowest = criterion.path("score").asDouble();
                    focus = criterion.path("key").asText(null);
                }
                ((ObjectNode) criterion).remove("score");
            }
            if (focus != null) {
                report.put("focusCriterion", focus);
            }
            report.put("scoresHidden", true);
            return mapper.writeValueAsString(report);
        } catch (Exception unreadable) {
            return reportJson;
        }
    }
}
