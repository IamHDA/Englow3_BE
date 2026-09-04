package com.englow3.ai.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class PersonalizedExamDtos {

    private PersonalizedExamDtos() {
    }

    enum Skill {
        LISTENING, READING, MIXED
    }

    enum Level {
        A1, A2, B1, B2, C1
    }

    record GenerateRequest(@NotNull Level targetLevel, @NotNull Skill skill, @Min(5) @Max(100) int questionCount,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal difficultyMin,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal difficultyMax, @Size(max = 100) String title,
            @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record ExamResponse(UUID examId, String title, String targetLevel, String skill, int questionCount,
            int durationSeconds, String status, List<SourceSummary> sources, Instant createdAt) {
    }

    record SourceSummary(int position, String sourceItemId, int partNumber, String skill, BigDecimal difficulty,
            List<String> conceptIds) {
    }
}
