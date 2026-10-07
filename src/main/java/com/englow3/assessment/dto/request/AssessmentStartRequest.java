package com.englow3.assessment.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record AssessmentStartRequest(@NotNull UUID clientKey, @Size(max = 100) String contentType,
        @Min(45) @Max(10485760) Long contentLength) {
}
