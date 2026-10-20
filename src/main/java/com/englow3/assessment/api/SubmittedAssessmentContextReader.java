package com.englow3.assessment.api;

import java.time.Instant;
import java.util.UUID;

/** Minimal completed work owned by the caller. No storage URLs, identity or hidden criterion scores. */
public interface SubmittedAssessmentContextReader {
    Review read(UUID attemptId);

    record Review(UUID attemptId, String skill, String title, String instructions, String learnerResponse,
            String feedback, Double reportedOverallBand, String source, Instant assessedAt) {
    }
}
