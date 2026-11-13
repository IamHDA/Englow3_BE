package com.englow3.assessment.dto.response;

import java.util.UUID;

import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTaskPurpose;
import com.englow3.assessment.entity.AssessmentTaskStatus;

public record AssessmentTaskResponse(UUID id, AssessmentSkill skill, String title, String taskType, String instructions,
        String rubricNotes, String sampleAnswer, int minimumWords, int timeLimitSeconds, AssessmentTaskStatus status,
        String reviewNote, long version, String targetLevel, AssessmentTaskPurpose purpose, String certificateType,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true, description = "Writing Task 1: the chart to describe, signed for a few hours") String imageUrl) {
    public static AssessmentTaskResponse from(AssessmentTaskResult t) {
        return new AssessmentTaskResponse(t.id(), t.skill(), t.title(), t.taskType(), t.instructions(), t.rubricNotes(),
                t.sampleAnswer(), t.minimumWords(), t.timeLimitSeconds(), t.status(), t.reviewNote(), t.version(),
                t.targetLevel(), t.purpose(), t.certificateType(), t.imageUrl());
    }
}
