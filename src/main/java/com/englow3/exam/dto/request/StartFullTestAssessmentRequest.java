package com.englow3.exam.dto.request;

import jakarta.validation.constraints.*;

public record StartFullTestAssessmentRequest(@NotNull java.util.UUID taskId, @Size(max = 100) String contentType,
        @Min(45) @Max(10485760) Long contentLength) {
}
