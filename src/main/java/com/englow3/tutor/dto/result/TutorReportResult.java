package com.englow3.tutor.dto.result;

import java.time.Instant;
import java.util.UUID;
import com.englow3.tutor.entity.TutorReport;

public record TutorReportResult(UUID id, UUID messageId, int revision, String question, String answer,
        String learnerNote, String status, UUID assignedTo, String reviewerNote, String correction, Instant createdAt,
        Instant resolvedAt) {
    public static TutorReportResult of(TutorReport report, boolean detail) {
        return new TutorReportResult(report.getId(), report.getMessageId(), report.getRevision(),
                detail ? report.getQuestionSnapshot() : null, detail ? report.getAnswerSnapshot() : null,
                detail ? report.getLearnerNote() : null, report.getStatus(), report.getAssignedTo(),
                detail ? report.getReviewerNote() : null, detail ? report.getProposedCorrection() : null,
                report.getCreatedAt(), report.getResolvedAt());
    }
}
