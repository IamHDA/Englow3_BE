package com.englow3.assessment.api;

import java.util.UUID;

/** Exam orchestrates full-test attempts; assessment owns their drafts, uploads and recording limits. */
public interface FullTestAssessmentStarter {
    Prepared start(UUID taskId, UUID requestKey, String contentType, Long contentLength, UUID existingAttemptId);

    record Prepared(UUID attemptId, String uploadUrl) {
    }
}
