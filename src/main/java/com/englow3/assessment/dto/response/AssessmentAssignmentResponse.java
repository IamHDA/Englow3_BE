package com.englow3.assessment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.englow3.assessment.dto.result.AssessmentAssignmentResult;

public record AssessmentAssignmentResponse(UUID attemptId,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true) UUID reviewerId,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String reviewerName, long version,
        List<Entry> history) {
    public record Entry(UUID id, UUID assignedByUserId, UUID reviewerId, String note, Instant createdAt) {
    }

    public static AssessmentAssignmentResponse from(AssessmentAssignmentResult r) {
        return new AssessmentAssignmentResponse(r.attemptId(), r.reviewerId(), r.reviewerName(), r.version(),
                r.history().stream()
                        .map(e -> new Entry(e.id(), e.assignedByUserId(), e.reviewerId(), e.note(), e.createdAt()))
                        .toList());
    }
}
