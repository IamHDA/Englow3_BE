package com.englow3.assessment.entity;

import java.time.Instant;
import java.util.UUID;
import com.englow3.shared.persistence.BasePersistedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "assessment_assignments")
@Getter
public class AssessmentAssignment extends BasePersistedEntity {
    private UUID attemptId;
    private UUID assignedByUserId;
    private UUID reviewerId;
    private String note;
    private Instant createdAt;

    protected AssessmentAssignment() {
    }

    public static AssessmentAssignment of(UUID attemptId, UUID assignedBy, UUID reviewer, String note, Instant now) {
        var entry = new AssessmentAssignment();
        entry.id = UUID.randomUUID();
        entry.attemptId = attemptId;
        entry.assignedByUserId = assignedBy;
        entry.reviewerId = reviewer;
        entry.note = note;
        entry.createdAt = now;
        return entry;
    }
}
