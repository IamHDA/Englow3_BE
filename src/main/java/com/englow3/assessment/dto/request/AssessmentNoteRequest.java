package com.englow3.assessment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssessmentNoteRequest(@NotBlank @Size(max = 4000) String note) {
}
