package com.englow3.assessment.dto.response;

import java.util.UUID;
import com.englow3.assessment.entity.*;
import com.englow3.assessment.dto.result.AssessmentTaskResult;

public record AssessmentTaskResponse(UUID id, AssessmentSkill skill, String title, String taskType, String instructions,
        String rubricNotes, String sampleAnswer, int minimumWords, int timeLimitSeconds, AssessmentTaskStatus status,
        String reviewNote, long version) {
    public static AssessmentTaskResponse from(AssessmentTaskResult t) {
        return new AssessmentTaskResponse(t.id(), t.skill(), t.title(), t.taskType(), t.instructions(), t.rubricNotes(),
                t.sampleAnswer(), t.minimumWords(), t.timeLimitSeconds(), t.status(), t.reviewNote(), t.version());
    }
}
