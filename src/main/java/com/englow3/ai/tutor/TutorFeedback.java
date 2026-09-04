package com.englow3.ai.tutor;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_tutor_feedback")
class TutorFeedback {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    private Short rating;

    @Column(name = "report_reason")
    private String reportReason;

    private String comment;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TutorFeedback() {
    }

    static TutorFeedback create(UUID messageId, UUID userId, Short rating, String reason, String comment) {
        TutorFeedback feedback = new TutorFeedback();
        feedback.id = UUID.randomUUID();
        feedback.messageId = messageId;
        feedback.userId = userId;
        feedback.rating = rating;
        feedback.reportReason = reason;
        feedback.comment = comment;
        feedback.status = "OPEN";
        feedback.createdAt = Instant.now();
        return feedback;
    }
}
