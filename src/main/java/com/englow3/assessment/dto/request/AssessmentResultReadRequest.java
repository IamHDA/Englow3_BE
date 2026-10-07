package com.englow3.assessment.dto.request;

import jakarta.validation.constraints.*;

public record AssessmentResultReadRequest(@NotNull @PositiveOrZero Long version) {
}
