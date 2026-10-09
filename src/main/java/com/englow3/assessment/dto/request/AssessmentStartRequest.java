package com.englow3.assessment.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssessmentStartRequest(@NotNull UUID clientKey, @Size(max = 100) String contentType,
        @Min(45) @Max(10485760) Long contentLength) {
}
