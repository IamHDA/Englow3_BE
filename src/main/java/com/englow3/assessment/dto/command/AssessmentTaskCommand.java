package com.englow3.assessment.dto.command;

import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTaskPurpose;

public record AssessmentTaskCommand(AssessmentSkill skill, String title, String taskType, String instructions,
        String rubricNotes, String sampleAnswer, int minimumWords, int timeLimitSeconds, String targetLevel,
        AssessmentTaskPurpose purpose, String certificateType) {

    /** The authoring request as a command - shared by the controller and the generation writer. */
    public static AssessmentTaskCommand of(com.englow3.assessment.dto.request.AssessmentTaskRequest r) {
        return new AssessmentTaskCommand(r.skill(), r.title(), r.taskType(), r.instructions(), r.rubricNotes(),
                r.sampleAnswer(), r.minimumWords(), r.timeLimitSeconds(), r.targetLevel(), r.purpose(),
                r.certificateType());
    }
}
