package com.englow3.ai.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.ai.evaluation.AiEvaluationService.EvaluationCandidate;
import com.englow3.ai.evaluation.AiEvaluationService.EvaluationCaseResult;
import com.englow3.ai.evaluation.AiEvaluationService.EvaluationWork;
import com.englow3.ai.foundation.AiEvaluationGateway;
import com.englow3.ai.foundation.AiEvaluationGateway.EvaluationRequest;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
class AiEvaluationWorker {

    private final AiEvaluationService service;
    private final AiEvaluationGateway gateway;
    private final ObjectMapper objectMapper;

    AiEvaluationWorker(AiEvaluationService service, AiEvaluationGateway gateway, ObjectMapper objectMapper) {
        this.service = service;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.ai.evaluation.worker-delay:5s}")
    void poll() {
        EvaluationWork work = service.claimNext();
        if (work == null) {
            return;
        }
        try {
            execute(work);
        } catch (AiProviderException exception) {
            service.fail(work.candidate().runId(), exception.code());
        } catch (RuntimeException exception) {
            service.fail(work.candidate().runId(), "AI_EVALUATION_EXECUTION_FAILED");
        }
    }

    private void execute(EvaluationWork work) {
        EvaluationCandidate candidate = work.candidate();
        List<EvaluationCaseResult> results = new ArrayList<>();
        List<List<EvaluationCaseResult>> caseGroups = new ArrayList<>();
        for (var evaluationCase : work.cases()) {
            List<EvaluationCaseResult> caseResults = new ArrayList<>();
            Map<String, Object> variables = objectMapper.convertValue(evaluationCase.variables(),
                    new TypeReference<>() {
                    });
            String systemPrompt = AiPromptService.renderText(candidate.systemTemplate(), variables);
            String userPrompt = AiPromptService.renderText(candidate.userTemplate(), variables);
            for (int attempt = 1; attempt <= candidate.repetitions(); attempt++) {
                var providerResult = gateway.evaluate(new EvaluationRequest(candidate.provider(), candidate.model(),
                        systemPrompt, userPrompt, candidate.temperature().doubleValue(), candidate.maxOutputTokens(),
                        candidate.inputCost(), candidate.outputCost()));
                EvaluationCaseResult result = AiEvaluationContract.evaluate(objectMapper, evaluationCase.expected(),
                        providerResult);
                service.saveResult(candidate.runId(), evaluationCase.id(), attempt, result);
                results.add(result);
                caseResults.add(result);
            }
            caseGroups.add(caseResults);
        }
        Summary summary = summarize(results, caseGroups);
        boolean hardPassed = summary.schemaSuccessRate().compareTo(candidate.schemaMin()) >= 0
                && summary.evidenceFidelity().compareTo(candidate.evidenceMin()) >= 0
                && summary.unsafeRate().compareTo(candidate.unsafeMax()) <= 0;
        boolean qualityRecommended = summary.scoreVariance().compareTo(candidate.varianceMax()) <= 0
                && summary.humanAgreement().compareTo(candidate.agreementMin()) >= 0
                && summary.latencyP95Ms() <= candidate.latencyP95MaxMs();
        ObjectNode json = objectMapper.createObjectNode().put("attempts", results.size())
                .put("schemaSuccessRate", summary.schemaSuccessRate())
                .put("evidenceFidelity", summary.evidenceFidelity()).put("unsafeRate", summary.unsafeRate())
                .put("scoreVariance", summary.scoreVariance()).put("humanAgreement", summary.humanAgreement())
                .put("latencyP95Ms", summary.latencyP95Ms()).put("inputTokens", summary.inputTokens())
                .put("outputTokens", summary.outputTokens()).put("estimatedCost", summary.estimatedCost())
                .put("hardGatesPassed", hardPassed).put("qualityRecommended", qualityRecommended);
        var baseline = service.baselineSummary(candidate.baselineRunId());
        if (baseline != null) {
            json.put("baselineRunId", candidate.baselineRunId().toString());
            json.put("schemaSuccessDelta",
                    summary.schemaSuccessRate().subtract(baseline.path("schemaSuccessRate").decimalValue()));
            json.put("evidenceFidelityDelta",
                    summary.evidenceFidelity().subtract(baseline.path("evidenceFidelity").decimalValue()));
            json.put("unsafeRateDelta", summary.unsafeRate().subtract(baseline.path("unsafeRate").decimalValue()));
            json.put("latencyP95DeltaMs", summary.latencyP95Ms() - baseline.path("latencyP95Ms").asInt());
            json.put("estimatedCostDelta",
                    summary.estimatedCost().subtract(baseline.path("estimatedCost").decimalValue()));
        }
        service.finish(candidate.runId(), json, hardPassed);
    }

    static Summary summarize(List<EvaluationCaseResult> results) {
        return summarize(results, List.of(results));
    }

    private static Summary summarize(List<EvaluationCaseResult> results, List<List<EvaluationCaseResult>> caseGroups) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Evaluation run has no results");
        }
        BigDecimal total = BigDecimal.valueOf(results.size());
        BigDecimal schema = BigDecimal.valueOf(results.stream().filter(EvaluationCaseResult::schemaSuccess).count())
                .divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal evidence = results.stream().map(EvaluationCaseResult::evidenceFidelity)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal unsafe = BigDecimal.valueOf(results.stream().filter(EvaluationCaseResult::unsafeResponse).count())
                .divide(total, 4, RoundingMode.HALF_UP);
        List<BigDecimal> caseVariances = caseGroups.stream().map(group -> variance(
                group.stream().map(EvaluationCaseResult::automaticScore).filter(java.util.Objects::nonNull).toList()))
                .toList();
        BigDecimal variance = caseVariances.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(caseVariances.size()), 4, RoundingMode.HALF_UP);
        List<BigDecimal> deltas = results.stream().map(EvaluationCaseResult::scoreDelta)
                .filter(java.util.Objects::nonNull).toList();
        BigDecimal agreement = deltas.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.ONE.subtract(deltas.stream().map(delta -> delta.min(BigDecimal.valueOf(100)))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(deltas.size() * 100L), 4, RoundingMode.HALF_UP));
        List<Integer> latency = results.stream().map(EvaluationCaseResult::latencyMs).sorted().toList();
        int p95 = latency.get(Math.max(0, (int) Math.ceil(latency.size() * 0.95) - 1));
        int inputTokens = results.stream().mapToInt(EvaluationCaseResult::inputTokens).sum();
        int outputTokens = results.stream().mapToInt(EvaluationCaseResult::outputTokens).sum();
        BigDecimal cost = results.stream().map(EvaluationCaseResult::estimatedCost).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        return new Summary(schema, evidence, unsafe, variance, agreement, p95, inputTokens, outputTokens, cost);
    }

    private static BigDecimal variance(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal count = BigDecimal.valueOf(values.size());
        BigDecimal mean = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(count, 8,
                RoundingMode.HALF_UP);
        return values.stream().map(value -> value.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(count, 4, RoundingMode.HALF_UP);
    }

    record Summary(BigDecimal schemaSuccessRate, BigDecimal evidenceFidelity, BigDecimal unsafeRate,
            BigDecimal scoreVariance, BigDecimal humanAgreement, int latencyP95Ms, int inputTokens, int outputTokens,
            BigDecimal estimatedCost) {
    }
}
