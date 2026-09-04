package com.englow3.ai.placement;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class AdaptivePlacementDtos {

    private AdaptivePlacementDtos() {
    }

    record StartRequest(@NotNull UUID fallbackExamId, @Min(5) @Max(30) int minItems, @Min(5) @Max(50) int maxItems) {
    }

    record AnswerRequest(@NotBlank @Size(max = 1) String selectedLabel,
            @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record Option(String label, String text) {
    }

    record Item(String itemId, String question, int partNumber, List<Option> options) {
    }

    record AttemptResponse(UUID attemptId, String mode, String status, Integer calibrationVersion,
            UUID fallbackAttemptId, int responseCount, Double theta, Double standardError, String assessedLevel,
            Item nextItem) {
    }

    record CalibrationItem(@NotBlank String itemId,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("5") double discrimination,
            @DecimalMin("-6") @DecimalMax("6") double difficulty,
            @DecimalMin("0") @DecimalMax(value = "0.5", inclusive = false) double guessing, @Min(0) int responseCount,
            @DecimalMin(value = "0", inclusive = false) Double standardError) {
    }

    record CalibrationImportRequest(@Min(1) int version, @Min(30) int minimumResponses,
            @NotEmpty @Size(max = 5000) List<@Valid CalibrationItem> items) {
    }

    record CalibrationResponse(int version, String status, int minimumResponses, int itemCount, String sourceHash) {
    }
}
