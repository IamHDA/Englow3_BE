package com.englow3.assessment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssessmentDraftRequest(@NotNull @Size(max = 12000) String answerText, @Min(0) long version) {
}
