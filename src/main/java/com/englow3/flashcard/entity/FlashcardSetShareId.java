package com.englow3.flashcard.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class FlashcardSetShareId implements Serializable {

    @Column(name = "flashcard_set_id", nullable = false, updatable = false)
    private UUID flashcardSetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    protected FlashcardSetShareId() {
    }

    public FlashcardSetShareId(UUID flashcardSetId, UUID userId) {
        this.flashcardSetId = flashcardSetId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FlashcardSetShareId that && Objects.equals(flashcardSetId, that.flashcardSetId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flashcardSetId, userId);
    }
}
