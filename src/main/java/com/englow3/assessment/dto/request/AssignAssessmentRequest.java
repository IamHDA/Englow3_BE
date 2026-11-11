package com.englow3.assessment.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssignAssessmentRequest(@io.swagger.v3.oas.annotations.media.Schema(nullable = true) UUID reviewerId,
        @NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 2000) String note) {
}
