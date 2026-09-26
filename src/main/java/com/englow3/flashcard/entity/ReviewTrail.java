package com.englow3.flashcard.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class ReviewTrail {

    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    private String reviewNote;

    public void markSubmitted(Instant now) {
        submittedForReviewAt = now;
    }

    public void markApproved(UUID reviewerId, Instant now) {
        reviewedByUserId = reviewerId;
        reviewedAt = now;
        reviewNote = null;
    }

    public void markRejected(UUID reviewerId, String note, Instant now) {
        if (note == null || note.isBlank()) {
            throw new BadRequestException("REVIEW_NOTE_REQUIRED", "A rejection must say why");
        }
        reviewedByUserId = reviewerId;
        reviewedAt = now;
        reviewNote = note.strip();
    }
}
