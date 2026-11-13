package com.englow3.assessment.dto.response;

import com.englow3.assessment.dto.result.AssessmentUploadResult;

public record AssessmentUploadResponse(AssessmentAttemptResponse attempt, String uploadUrl) {
    public static AssessmentUploadResponse from(AssessmentUploadResult r) {
        return new AssessmentUploadResponse(AssessmentAttemptResponse.from(r.attempt()), r.uploadUrl());
    }
}
