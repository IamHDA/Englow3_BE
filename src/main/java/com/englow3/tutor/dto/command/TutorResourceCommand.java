package com.englow3.tutor.dto.command;

import jakarta.validation.constraints.*;

public record TutorResourceCommand(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 12000) String body,
        @Min(0) long rowVersion) {
}
