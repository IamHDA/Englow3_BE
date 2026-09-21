package com.englow3.learning.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "flashcard_sets")
@Getter
public class FlashcardSet {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String topic;

    @Column(name = "target_level")
    private String targetLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlashcardSetStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected FlashcardSet() {
    }

    public static FlashcardSet draft(String slug, String name, String description, String topic, String targetLevel,
            UUID createdByUserId) {
        FlashcardSet set = new FlashcardSet();
        set.id = UUID.randomUUID();
        set.slug = slug;
        set.name = name;
        set.description = description == null ? "" : description;
        set.topic = topic;
        set.targetLevel = targetLevel;
        set.status = FlashcardSetStatus.DRAFT;
        set.createdByUserId = createdByUserId;
        return set;
    }

    /**
     * Publishing an empty set would put a set in the catalogue that opens onto nothing, so the count is checked here
     * rather than left to whoever happens to look.
     */
    public void publish(long cardCount, Instant now) {
        if (status != FlashcardSetStatus.DRAFT) {
            throw new ConflictException("FLASHCARD_SET_NOT_DRAFT",
                    "Only a draft set can be published; this one is %s".formatted(status));
        }
        if (cardCount == 0) {
            throw new ConflictException("FLASHCARD_SET_EMPTY", "A set with no cards cannot be published");
        }
        this.status = FlashcardSetStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void archive() {
        if (status == FlashcardSetStatus.ARCHIVED) {
            throw new ConflictException("FLASHCARD_SET_ALREADY_ARCHIVED", "This set is already archived");
        }
        this.status = FlashcardSetStatus.ARCHIVED;
    }
}
