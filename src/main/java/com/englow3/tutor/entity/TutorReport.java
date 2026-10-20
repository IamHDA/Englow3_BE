package com.englow3.tutor.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import com.englow3.shared.error.ConflictException;

@Entity
@Table(name = "tutor_reports")
@Getter
public class TutorReport {
    @Id
    private UUID id;
    private UUID messageId;
    private int revision;
    private UUID reporterId;
    private String questionSnapshot;
    private String answerSnapshot;
    private String learnerNote;
    private String status;
    private UUID assignedTo;
    private String reviewerNote;
    private String proposedCorrection;
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
    private Instant resolvedAt;

    protected TutorReport() {
    }

    public static TutorReport opened(TutorMessage answer, String question, UUID user, String note) {
        TutorReport report = new TutorReport();
        report.id = UUID.randomUUID();
        report.messageId = answer.getId();
        report.revision = answer.getRevision();
        report.reporterId = user;
        report.questionSnapshot = question;
        report.answerSnapshot = answer.getContent();
        report.learnerNote = note;
        report.status = "OPEN";
        return report;
    }

    public void assign(UUID staff) {
        requireUnresolved();
        assignedTo = staff;
        status = "ASSIGNED";
    }

    public void propose(String note, String correction) {
        if (!"ASSIGNED".equals(status) && !"PROPOSED".equals(status))
            throw new ConflictException("TUTOR_REPORT_STATE", "Assign the report before reviewing");
        reviewerNote = note;
        proposedCorrection = correction == null || correction.isBlank() ? null : correction.strip();
        status = "PROPOSED";
    }

    public void resolve(boolean dismiss, String note, Instant now) {
        requireUnresolved();
        if (!dismiss && !"PROPOSED".equals(status))
            throw new ConflictException("TUTOR_REPORT_STATE", "Review a proposal before resolving");
        status = dismiss ? "DISMISSED" : "RESOLVED";
        reviewerNote = note;
        resolvedAt = now;
    }

    private void requireUnresolved() {
        if ("RESOLVED".equals(status) || "DISMISSED".equals(status))
            throw new ConflictException("TUTOR_REPORT_CLOSED", "This report has already been resolved");
    }
}
