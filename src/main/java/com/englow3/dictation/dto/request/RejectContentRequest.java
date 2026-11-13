package com.englow3.dictation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectContentRequest(@NotBlank @Size(max = 2000) String note) {
}
