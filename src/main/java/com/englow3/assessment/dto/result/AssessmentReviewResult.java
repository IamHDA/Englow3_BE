package com.englow3.assessment.dto.result;

import java.time.Instant;
import java.util.UUID;

public record AssessmentReviewResult(UUID id, UUID reviewerId, String reviewerName, String previousReport,
        String report, String note, Instant createdAt) {
}
