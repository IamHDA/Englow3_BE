package com.englow3.flashcard.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One friend a SELECTED_FRIENDS set is shown to. A plain row: it grants access only while the reader is still the
 * owner's friend, and that check spans the friendship module, so it lives in the service, not here.
 */
@Entity
@Table(name = "flashcard_set_shares")
@Getter
public class FlashcardSetShare {

    @EmbeddedId
    private FlashcardSetShareId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FlashcardSetShare() {
    }

    public static FlashcardSetShare of(UUID flashcardSetId, UUID userId, Instant now) {
        FlashcardSetShare share = new FlashcardSetShare();
        share.id = new FlashcardSetShareId(flashcardSetId, userId);
        share.createdAt = now;
        return share;
    }
}
