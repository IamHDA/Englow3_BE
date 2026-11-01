package com.englow3.translation.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A set of Vietnamese sentences to put into English, with the review trail every authored content type carries. The
 * trail is inline rather than borrowed: persistence types do not cross module boundaries.
 */
@Entity
@Table(name = "translation_sets")
@Getter
public class TranslationSet {

    @jakarta.persistence.Version
    private long version;

    @Column(name = "authoring_updated_at", nullable = false)
    private Instant authoringUpdatedAt = Instant.now();

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String topic;

    @Column(name = "target_level")
    private String targetLevel;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranslationSetStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected TranslationSet() {
    }

    public static TranslationSet draft(String slug, String title, String topic, String targetLevel, String description,
            UUID createdByUserId) {
        TranslationSet set = new TranslationSet();
        set.id = UUID.randomUUID();
        set.slug = slug;
        set.title = title;
        set.topic = topic;
        set.targetLevel = targetLevel;
        set.description = description;
        set.status = TranslationSetStatus.DRAFT;
        set.createdByUserId = createdByUserId;
        return set;
    }

    /** Publishing straight from a draft, for an administrator who holds the approval power anyway. */
    public void publish(long itemCount, Instant now) {
        if (status != TranslationSetStatus.DRAFT) {
            throw new ConflictException("TRANSLATION_SET_NOT_DRAFT",
                    "Only a draft set can be published; this one is %s".formatted(status));
        }
        requireItems(itemCount);
        this.status = TranslationSetStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void submitForReview(long itemCount, Instant now) {
        if (status != TranslationSetStatus.DRAFT && status != TranslationSetStatus.REJECTED) {
            throw new ConflictException("TRANSLATION_SET_NOT_SUBMITTABLE",
                    "Only a draft or rejected set can be submitted; this one is %s".formatted(status));
        }
        requireItems(itemCount);
        this.status = TranslationSetStatus.PENDING_REVIEW;
        this.submittedForReviewAt = now;
    }

    public void approve(UUID reviewerId, Instant now) {
        requirePendingReview("approved");
        this.status = TranslationSetStatus.PUBLISHED;
        this.publishedAt = now;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = null;
    }

    public void reject(UUID reviewerId, String note, Instant now) {
        requirePendingReview("rejected");
        if (note == null || note.isBlank()) {
            throw new BadRequestException("REVIEW_NOTE_REQUIRED", "A rejection must say why");
        }
        this.status = TranslationSetStatus.REJECTED;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = note.strip();
    }

    /**
     * Only before publication. Learners' attempts point at the items, so a published set is never rewritten under them
     * - a correction is a new set.
     */
    public void updateDraft(String slug, String title, String topic, String targetLevel, String description,
            long expectedVersion, Instant now) {
        if ((status != TranslationSetStatus.DRAFT && status != TranslationSetStatus.REJECTED) || publishedAt != null) {
            throw new ConflictException("CONTENT_NOT_EDITABLE",
                    "Only an unpublished draft or rejected item can be edited");
        }
        if (version != expectedVersion) {
            throw new ConflictException("CONTENT_CHANGED", "This item changed. Reload before saving again");
        }
        this.slug = slug;
        this.title = title;
        this.topic = topic;
        this.targetLevel = targetLevel;
        this.description = description;
        this.authoringUpdatedAt = now;
    }

    /** Back to where it was: published if it ever was, otherwise a draft. */
    public void restore() {
        if (status != TranslationSetStatus.ARCHIVED) {
            throw new ConflictException("TRANSLATION_SET_NOT_ARCHIVED", "Only an archived set can be restored");
        }
        this.status = publishedAt != null ? TranslationSetStatus.PUBLISHED : TranslationSetStatus.DRAFT;
    }

    public void archive() {
        if (status == TranslationSetStatus.ARCHIVED) {
            throw new ConflictException("TRANSLATION_SET_ALREADY_ARCHIVED", "This set is already archived");
        }
        this.status = TranslationSetStatus.ARCHIVED;
    }

    public boolean isPublished() {
        return status == TranslationSetStatus.PUBLISHED;
    }

    private void requirePendingReview(String verb) {
        if (status != TranslationSetStatus.PENDING_REVIEW) {
            throw new ConflictException("TRANSLATION_SET_NOT_PENDING_REVIEW",
                    "Only a set waiting on review can be %s; this one is %s".formatted(verb, status));
        }
    }

    private static void requireItems(long itemCount) {
        if (itemCount < 1) {
            throw new ConflictException("TRANSLATION_SET_EMPTY", "A set needs at least one sentence");
        }
    }
}
