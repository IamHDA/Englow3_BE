package com.englow3.assessment.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.englow3.assessment.entity.*;
import com.englow3.assessment.dto.result.AssessmentAttemptResult;

public record AssessmentAttemptResponse(UUID id, UUID taskId, AssessmentSkill skill, AssessmentTaskResponse task,
        AssessmentAttemptStatus status, String answerText, String audioUrl, String recognizedText, String report,
        String source, String errorCode, int wordCount, long version, Instant createdAt, Instant submittedAt,
        Instant assessedAt, UUID learnerId, String learnerName) {
    public static AssessmentAttemptResponse from(AssessmentAttemptResult a) {
        return new AssessmentAttemptResponse(a.id(), a.taskId(), a.skill(), AssessmentTaskResponse.from(a.task()),
                a.status(), a.answerText(), a.audioUrl(), a.recognizedText(), a.report(), a.source(), a.errorCode(),
                a.wordCount(), a.version(), a.createdAt(), a.submittedAt(), a.assessedAt(), a.learnerId(),
                a.learnerName());
    }
}
