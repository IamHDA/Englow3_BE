package com.englow3.assessment.dto.result;

import java.util.UUID;
import java.time.Instant;

public record AssessmentNotificationResult(UUID attemptId, String title, String skill, long version,
        Instant assessedAt) {
}
