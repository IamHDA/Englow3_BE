package com.englow3.assessment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssessmentReviewRequest(@NotBlank @Size(max = 20000) String report,
        @NotBlank @Size(max = 4000) String note, @Size(max = 12000) String transcript,
        @NotNull @PositiveOrZero Long version) {
}
