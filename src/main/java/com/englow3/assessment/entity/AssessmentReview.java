package com.englow3.assessment.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_reviews")
@lombok.Getter
public class AssessmentReview {
    @Id
    private UUID id;
    private UUID attemptId;
    private UUID reviewedByUserId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String previousReport;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String report;
    @Column(columnDefinition = "text")
    private String note;
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected AssessmentReview() {
    }

    public static AssessmentReview of(UUID attempt, UUID reviewer, String previous, String report, String note) {
        AssessmentReview r = new AssessmentReview();
        r.id = UUID.randomUUID();
        r.attemptId = attempt;
        r.reviewedByUserId = reviewer;
        r.previousReport = previous;
        r.report = report;
        r.note = note;
        return r;
    }
}
