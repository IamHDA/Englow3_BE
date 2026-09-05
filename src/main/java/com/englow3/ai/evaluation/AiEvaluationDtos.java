package com.englow3.ai.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.ai.foundation.AiCapability;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AiEvaluationDtos {

    private AiEvaluationDtos() {
    }

    public record CaseRequest(@NotBlank @Size(max = 100) String caseKey, @AssertTrue boolean synthetic,
            @NotNull JsonNode promptVariables, @NotNull JsonNode expectedContract) {
    }

    public record SuiteRequest(@NotBlank @Size(max = 100) String suiteKey, @Min(1) int version,
            @NotNull AiCapability capability, @Min(1) @Max(10) int repetitions,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal schemaSuccessMin,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal evidenceFidelityMin,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal unsafeRateMax,
            @NotNull @DecimalMin("0") BigDecimal scoreVarianceMax,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal humanAgreementMin, @Min(1) int latencyP95MaxMs,
            @NotEmpty @Size(max = 200) List<@Valid CaseRequest> cases) {
    }

    public record RunRequest(@NotNull UUID suiteId, @NotBlank @Size(max = 50) String provider,
            @NotBlank @Size(max = 100) String model, @NotNull UUID promptTemplateId, @Min(1) int promptVersion,
            @NotNull @DecimalMin("0") @DecimalMax("2") BigDecimal temperature, @Min(1) @Max(32768) int maxOutputTokens,
            @NotNull @DecimalMin("0") BigDecimal inputCostPerMillion,
            @NotNull @DecimalMin("0") BigDecimal outputCostPerMillion, UUID baselineRunId) {
    }

    public record DecisionRequest(boolean accepted, @NotBlank @Size(max = 2000) String reason) {
    }

    public record SuiteResponse(UUID id, String suiteKey, int version, String capability, int repetitions,
            String suiteHash, int caseCount, Instant createdAt) {
    }

    public record RunResponse(UUID id, UUID candidateId, String status, Boolean hardGatesPassed,
            Boolean humanQualityPassed, JsonNode summary, String failureCode, String decisionReason, Instant createdAt,
            Instant completedAt, Instant decidedAt) {
    }
}
