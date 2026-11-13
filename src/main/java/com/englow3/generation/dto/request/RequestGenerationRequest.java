package com.englow3.generation.dto.request;

import com.englow3.generation.entity.GenerationKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestGenerationRequest(@NotNull GenerationKind kind,
        @Size(max = 1000) @Schema(nullable = true, description = "Optional: what the author wants - topics, difficulty, question types. The structural rules still apply.") String brief) {
}
