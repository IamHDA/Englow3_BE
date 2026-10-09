package com.englow3.exam.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveAuthoringRequest(@PositiveOrZero Long version, @NotNull @Valid CreateExamRequest metadata,
        @NotNull @Valid UpdateExamContentRequest content) {
}
