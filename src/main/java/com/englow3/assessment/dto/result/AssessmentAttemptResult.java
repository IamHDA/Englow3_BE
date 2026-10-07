package com.englow3.assessment.dto.result;

import java.time.Instant;
import java.util.UUID;
import com.englow3.assessment.entity.*;

public record AssessmentAttemptResult(UUID id, UUID taskId, AssessmentSkill skill, AssessmentTaskResult task,
        AssessmentAttemptStatus status, String answerText, String audioUrl, String recognizedText, String report,
        String source, String errorCode, int wordCount, long version, Instant createdAt, Instant submittedAt,
        Instant assessedAt, UUID learnerId, String learnerName) {
}
