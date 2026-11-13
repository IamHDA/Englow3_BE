package com.englow3.tutor.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameTutorCommand(@NotBlank @Size(max = 200) String title) {
}
