package com.englow3.assessment.dto.result;

public record AssessmentWorkloadResult(long drafts, long rejected, long pendingReview, long published, long needsReview,
        long failed, long completed) {
}
