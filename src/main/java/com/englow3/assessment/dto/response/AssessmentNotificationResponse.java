package com.englow3.assessment.dto.response;

import java.util.UUID;
import java.time.Instant;
import com.englow3.assessment.dto.result.AssessmentNotificationResult;

public record AssessmentNotificationResponse(UUID attemptId, String title, String skill, long version,
        Instant assessedAt) {
    public static AssessmentNotificationResponse from(AssessmentNotificationResult r) {
        return new AssessmentNotificationResponse(r.attemptId(), r.title(), r.skill(), r.version(), r.assessedAt());
    }
}
