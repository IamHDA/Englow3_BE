package com.englow3.assessment.dto.request;

import jakarta.validation.constraints.*;

public record AssessmentDraftRequest(@NotNull @Size(max = 12000) String answerText, @Min(0) long version) {
}
