package com.englow3.ai.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.englow3.ai.evaluation.AiEvaluationService.EvaluationCaseResult;
import com.englow3.ai.foundation.AiEvaluationGateway.EvaluationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

final class AiEvaluationContract {

    private AiEvaluationContract() {
    }

    static EvaluationCaseResult evaluate(ObjectMapper objectMapper, JsonNode expected, EvaluationResult result) {
        List<String> violations = new ArrayList<>();
        JsonNode output;
        boolean parsed = true;
        try {
            output = objectMapper.readTree(result.content());
        } catch (Exception exception) {
            output = objectMapper.createObjectNode();
            parsed = false;
            violations.add("INVALID_JSON");
        }
        boolean schemaSuccess = parsed && output.isObject();
        for (JsonNode field : expected.path("requiredFields")) {
            if (!output.hasNonNull(field.asText())) {
                schemaSuccess = false;
                violations.add("MISSING_FIELD:" + field.asText());
            }
        }
        String normalizedOutput = result.content().toLowerCase(Locale.ROOT);
        int evidenceTotal = expected.path("requiredEvidence").size();
        int evidenceFound = 0;
        for (JsonNode evidence : expected.path("requiredEvidence")) {
            if (normalizedOutput.contains(evidence.asText().toLowerCase(Locale.ROOT))) {
                evidenceFound++;
            } else {
                violations.add("MISSING_EVIDENCE");
            }
        }
        BigDecimal evidenceFidelity = evidenceTotal == 0 ? BigDecimal.ONE
                : BigDecimal.valueOf(evidenceFound).divide(BigDecimal.valueOf(evidenceTotal), 4, RoundingMode.HALF_UP);
        boolean unsafe = false;
        for (JsonNode forbidden : expected.path("forbiddenTerms")) {
            if (normalizedOutput.contains(forbidden.asText().toLowerCase(Locale.ROOT))) {
                unsafe = true;
                violations.add("FORBIDDEN_TERM");
            }
        }
        BigDecimal automaticScore = score(output, expected, normalizedOutput);
        BigDecimal humanGold = decimal(expected.path("humanGoldScore"));
        BigDecimal scoreDelta = automaticScore == null || humanGold == null ? null
                : automaticScore.subtract(humanGold).abs();
        ArrayNode violationJson = objectMapper.createArrayNode();
        violations.forEach(violationJson::add);
        return new EvaluationCaseResult(schemaSuccess, evidenceFidelity, unsafe, automaticScore, scoreDelta,
                result.latencyMs(), result.inputTokens(), result.outputTokens(), result.estimatedCost(),
                AiEvaluationService.hash(result.content()), violationJson);
    }

    private static BigDecimal score(JsonNode output, JsonNode expected, String normalizedOutput) {
        String scoreField = expected.path("scoreField").asText();
        if (!scoreField.isBlank() && output.path(scoreField).isNumber()) {
            return output.path(scoreField).decimalValue();
        }
        JsonNode expectedContains = expected.path("expectedContains");
        if (!expectedContains.isArray() || expectedContains.isEmpty()) {
            return null;
        }
        long found = 0;
        for (JsonNode phrase : expectedContains) {
            if (normalizedOutput.contains(phrase.asText().toLowerCase(Locale.ROOT))) {
                found++;
            }
        }
        return BigDecimal.valueOf(found * 100L).divide(BigDecimal.valueOf(expectedContains.size()), 4,
                RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(JsonNode value) {
        return value.isNumber() ? value.decimalValue() : null;
    }
}
