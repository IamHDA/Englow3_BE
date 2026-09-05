package com.englow3.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.englow3.ai.foundation.AiEvaluationGateway.EvaluationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiEvaluationContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void measuresSchemaEvidenceSafetyAndHumanGoldAgreementWithoutPersistingOutput() throws Exception {
        var expected = objectMapper.readTree("""
                {
                  "requiredFields":["answer","evidence"],
                  "requiredEvidence":["approved fact"],
                  "forbiddenTerms":["api-key-secret"],
                  "scoreField":"score",
                  "humanGoldScore":85
                }
                """);
        var provider = new EvaluationResult("{\"answer\":\"Supported\",\"evidence\":\"approved fact\",\"score\":80}",
                "model", 10, 5, new BigDecimal("0.001"), 120);

        var result = AiEvaluationContract.evaluate(objectMapper, expected, provider);

        assertThat(result.schemaSuccess()).isTrue();
        assertThat(result.evidenceFidelity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.unsafeResponse()).isFalse();
        assertThat(result.scoreDelta()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(result.outputHash()).hasSize(64);
    }

    @Test
    void invalidJsonAndForbiddenContentFailDeterministicChecks() throws Exception {
        var expected = objectMapper.readTree("""
                {"requiredFields":["answer"],"forbiddenTerms":["secret"]}
                """);
        var provider = new EvaluationResult("not-json secret", "model", 1, 1, BigDecimal.ZERO, 10);

        var result = AiEvaluationContract.evaluate(objectMapper, expected, provider);

        assertThat(result.schemaSuccess()).isFalse();
        assertThat(result.unsafeResponse()).isTrue();
        assertThat(result.violations().toString()).contains("INVALID_JSON", "FORBIDDEN_TERM");
    }
}
