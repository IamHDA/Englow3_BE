package com.englow3.assessment.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssessmentAssignmentResult(UUID attemptId, UUID reviewerId, String reviewerName, long version,
        List<Entry> history) {
    public record Entry(UUID id, UUID assignedByUserId, UUID reviewerId, String note, Instant createdAt) {
    }
}
