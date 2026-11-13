package com.englow3.assessment.dto.response;

import com.englow3.assessment.dto.result.AssessmentWorkloadResult;

public record AssessmentWorkloadResponse(long drafts, long rejected, long pendingReview, long published,
        long needsReview, long failed, long completed) {
    public static AssessmentWorkloadResponse from(AssessmentWorkloadResult r) {
        return new AssessmentWorkloadResponse(r.drafts(), r.rejected(), r.pendingReview(), r.published(),
                r.needsReview(), r.failed(), r.completed());
    }
}
