package com.englow3.assessment.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.englow3.assessment.dto.result.AssessmentReviewResult;

public record AssessmentReviewResponse(UUID id, UUID reviewerId, String reviewerName, String previousReport,
        String report, String note, Instant createdAt) {
    public static AssessmentReviewResponse from(AssessmentReviewResult r) {
        return new AssessmentReviewResponse(r.id(), r.reviewerId(), r.reviewerName(), r.previousReport(), r.report(),
                r.note(), r.createdAt());
    }
}
