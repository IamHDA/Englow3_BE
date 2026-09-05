package com.englow3.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.ai.evaluation.AiEvaluationService.EvaluationCaseResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class AiEvaluationWorkerTest {
    @Nested
    class Success {

        @Test
        void summarizesRepeatedRunsWithVarianceAgreementLatencyTokensAndCost() {
            var noViolations = JsonNodeFactory.instance.arrayNode();
            List<EvaluationCaseResult> results = List.of(
                    new EvaluationCaseResult(true, BigDecimal.ONE, false, new BigDecimal("80"), new BigDecimal("5"),
                            100, 10, 5, new BigDecimal("0.001"), "a".repeat(64), noViolations),
                    new EvaluationCaseResult(true, new BigDecimal("0.5"), false, new BigDecimal("90"),
                            new BigDecimal("5"), 300, 12, 7, new BigDecimal("0.002"), "b".repeat(64), noViolations));

            var summary = AiEvaluationWorker.summarize(results);

            assertThat(summary.schemaSuccessRate()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(summary.evidenceFidelity()).isEqualByComparingTo(new BigDecimal("0.7500"));
            assertThat(summary.scoreVariance()).isEqualByComparingTo(new BigDecimal("25.0000"));
            assertThat(summary.humanAgreement()).isEqualByComparingTo(new BigDecimal("0.9500"));
            assertThat(summary.latencyP95Ms()).isEqualTo(300);
            assertThat(summary.inputTokens()).isEqualTo(22);
            assertThat(summary.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.003"));
        }

    }

}
