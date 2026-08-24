package com.englow3.ai.learningpath;

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

    record CompleteItemRequest(@NotNull Boolean successful, @Size(max = 100) String sourceId) {
    }

    record PathItem(UUID id, int position, String conceptId, String nameEn, String nameVi, String domain,
            double mastery, String reason, String status) {
    }

    record PathResponse(UUID id, String status, String explanation, UUID explanationJobId, int dailyMinutes,
            List<PathItem> items) {
    }
}
