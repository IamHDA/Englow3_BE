package com.englow3.ai.governance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.ai.foundation.AiCapability;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class AiGovernanceDtos {

    private AiGovernanceDtos() {
    }

    enum ContentType {
        QUIZ, DICTATION, FLASHCARDS, GRAMMAR_LESSON
    }

    enum FeedbackCategory {
        INCORRECT, INAPPROPRIATE, UNSAFE, LOW_QUALITY, OTHER
    }

    enum FeedbackStatus {
        OPEN, INVESTIGATING, RESOLVED, DISMISSED
    }

    record GenerateDraftRequest(@NotNull ContentType contentType, @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10) String level, @Min(1) @Max(50) int itemCount,
            @NotBlank @Size(max = 2000) String instructions, @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record DraftResponse(UUID id, String contentType, String title, String level, String status, UUID jobId,
            JsonNode generatedContent, JsonNode validationReport, int revision, JsonNode publishedEntities,
            String reviewReason, Instant createdAt, Instant updatedAt) {
    }

    record ReviewRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    record UpdateDraftRequest(@NotBlank @Size(max = 200) String title, @NotNull JsonNode generatedContent) {
    }

    record FeedbackRequest(UUID aiJobId, @NotNull AiCapability capability, @NotNull FeedbackCategory category,
            @Size(max = 2000) String details) {
    }

    record FeedbackResponse(UUID id, String capability, String category, String status, String details,
            String resolution, Instant createdAt, Instant resolvedAt) {
    }

    record ResolveFeedbackRequest(@NotNull FeedbackStatus status, @NotBlank @Size(max = 2000) String resolution) {
    }

    record PromptTemplateRequest(@NotBlank @Size(max = 100) String templateKey,
            @NotBlank @Size(max = 500) String description) {
    }

    record PromptVersionRequest(@NotBlank String systemTemplate, @NotBlank String userTemplate,
            JsonNode responseSchema) {
    }

    record PromptSummary(UUID templateId, String templateKey, String description, Integer activeVersion,
            Instant updatedAt) {
    }

    record ModelPolicyRequest(@NotBlank @Size(max = 50) String provider, @NotBlank @Size(max = 100) String model,
            @NotNull @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            @Min(1) @Max(32768) int maxOutputTokens, @NotNull @DecimalMin("0.0") BigDecimal inputCostPerMillion,
            @NotNull @DecimalMin("0.0") BigDecimal outputCostPerMillion, boolean enabled) {
    }

    record ModelPolicyResponse(String capability, String provider, String model, BigDecimal temperature,
            int maxOutputTokens, BigDecimal inputCostPerMillion, BigDecimal outputCostPerMillion, boolean enabled,
            Instant updatedAt) {
    }

    record AiOperationsMetrics(long totalJobs, long successfulJobs, long failedJobs, long pendingJobs, long inputTokens,
            long outputTokens, BigDecimal estimatedCost, long openReports) {
    }
}
