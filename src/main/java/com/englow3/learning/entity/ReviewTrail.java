package com.englow3.learning.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

/**
 * Who reviewed a piece of content, when, and what they said.
 * <p>
 * Shared by the three content types in this module because the audit trail is genuinely the same in all three - the
 * same four columns and the same rule about the note. What it deliberately does not own is the status: each type keeps
 * its own status enum, so a change to a quiz's lifecycle cannot reach into a vocabulary set's. The transition check
 * therefore stays on each entity, three lines each, and only the stamping lives here.
 * <p>
 * The exam module has these fields inline rather than importing this type: persistence types do not cross module
 * boundaries, and the asymmetry is cheaper than the coupling would be.
 */
@Embeddable
@Getter
public class ReviewTrail {

    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    /** A plain UUID: {@code users} belongs to the user module. */
    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    private String reviewNote;

    public void markSubmitted(Instant now) {
        this.submittedForReviewAt = now;
    }

    /** Clears the note as well: it described content that no longer exists. */
    public void markApproved(UUID reviewerId, Instant now) {
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = null;
    }

    /**
     * @param note
     *            why, in the reviewer's words. Required: "rejected" on its own gives the author nothing to change, and
     *            the next submission would be a guess. One code for all three types because the rule and the message
     *            are the same, and three codes would be three things for a client to map.
     */
    public void markRejected(UUID reviewerId, String note, Instant now) {
        if (note == null || note.isBlank()) {
            throw new BadRequestException("REVIEW_NOTE_REQUIRED", "A rejection must say why");
        }
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = note.strip();
    }
}
