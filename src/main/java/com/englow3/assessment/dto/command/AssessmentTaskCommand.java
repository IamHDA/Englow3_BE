package com.englow3.assessment.dto.command;

import com.englow3.assessment.entity.AssessmentSkill;

public record AssessmentTaskCommand(AssessmentSkill skill, String title, String taskType, String instructions,
        String rubricNotes, String sampleAnswer, int minimumWords, int timeLimitSeconds) {
}
