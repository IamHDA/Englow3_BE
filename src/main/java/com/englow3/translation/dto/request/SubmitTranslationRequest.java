package com.englow3.translation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitTranslationRequest(@NotBlank @Size(max = 1000) String response) {
}
