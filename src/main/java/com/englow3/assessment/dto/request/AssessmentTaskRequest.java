package com.englow3.assessment.dto.request;

import com.englow3.assessment.entity.AssessmentSkill;
import jakarta.validation.constraints.*;

public record AssessmentTaskRequest(@NotNull AssessmentSkill skill, @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 30) String taskType, @NotBlank @Size(max = 12000) String instructions,
        @Size(max = 8000) String rubricNotes, @Size(max = 12000) String sampleAnswer,
        @Min(0) @Max(1000) int minimumWords, @Min(30) @Max(3600) int timeLimitSeconds) {
}
