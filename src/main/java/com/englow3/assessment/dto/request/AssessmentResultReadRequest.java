package com.englow3.assessment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AssessmentResultReadRequest(@NotNull @PositiveOrZero Long version) {
}
