package com.englow3.ai.learningpath;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class LearningPathDtos {

    private LearningPathDtos() {
    }

    record GenerateRequest(@Min(5) @Max(240) int dailyMinutes, @Min(3) @Max(30) int items, boolean regenerate,
            @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record PreferencesRequest(@Min(5) @Max(240) int dailyMinutes, @Min(3) @Max(30) int itemsPerPath) {
    }

    record CompleteItemRequest(@NotNull Boolean successful, @Min(0) @Max(86400) Integer durationSeconds,
            @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record SkipItemRequest(@NotBlank @Size(max = 500) String reason, @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record PostponeItemRequest(@NotNull Instant until, @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record ReplaceItemRequest(@NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record PathItem(UUID id, int position, String conceptId, String nameEn, String nameVi, String domain,
            double mastery, String contentType, String contentId, String reason, String status,
            Instant postponedUntil) {
    }

    record PathResponse(UUID id, String status, String explanation, UUID explanationJobId, int dailyMinutes,
            List<PathItem> items) {
    }
}
