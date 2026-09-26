package com.englow3.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateQuizRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug must be lowercase words separated by single hyphens") String slug,
        @NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
        @NotBlank @Size(max = 60) String category,
        @Pattern(regexp = "^(A1|A2|B1|B2|C1|C2)$", message = "Target level must be a CEFR band") String targetLevel,
        @Positive @Max(7200) int timeLimitSeconds, @Min(1) @Max(100) short passingScorePercent) {
}
