package com.englow3.roadmap.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetLevelCheckWeightRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{2,30}$", message = "A purpose code is upper-case letters and underscores") String purposeCode,
        @Min(0) @Max(100) int vocabulary, @Min(0) @Max(100) int listening, @Min(0) @Max(100) int speaking,
        @Min(0) @Max(100) int priority) {
}
