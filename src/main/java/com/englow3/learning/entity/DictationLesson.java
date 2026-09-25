package com.englow3.learning.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "dictation_lessons")
@Getter
public class DictationLesson {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DictationLessonStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Filled by the column default, never by this application - hence not insertable. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Embedded
    private ReviewTrail review = new ReviewTrail();

    /**
     * Never null. Hibernate loads an embeddable whose columns are all null as {@code null}, which overrides the
     * initializer above - so every draft read back before its first review had no trail, and submitting it failed.
     */
    public ReviewTrail getReview() {
        if (review == null) {
            review = new ReviewTrail();
        }
        return review;
    }

    protected DictationLesson() {
    }

    public static DictationLesson draft(String slug, String title, String topic, String targetLevel,
            UUID createdByUserId) {
        DictationLesson lesson = new DictationLesson();
        lesson.id = UUID.randomUUID();
        lesson.slug = slug;
        lesson.title = title;
        lesson.topic = topic;
        lesson.targetLevel = targetLevel;
        lesson.status = DictationLessonStatus.DRAFT;
        lesson.createdByUserId = createdByUserId;
        return lesson;
    }

    public void publish(long sentenceCount, Instant now) {
        if (status != DictationLessonStatus.DRAFT) {
            throw new ConflictException("DICTATION_LESSON_NOT_DRAFT",
                    "Only a draft lesson can be published; this one is %s".formatted(status));
        }
        if (sentenceCount == 0) {
            throw new ConflictException("DICTATION_LESSON_EMPTY", "A lesson with no sentences cannot be published");
        }
        this.status = DictationLessonStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /** Hands the lesson to an administrator, held to the same rule so the reviewer is not sent an empty one. */
    public void submitForReview(long sentenceCount, Instant now) {
        if (status != DictationLessonStatus.DRAFT && status != DictationLessonStatus.REJECTED) {
            throw new ConflictException("DICTATION_LESSON_NOT_SUBMITTABLE",
                    "Only a draft or rejected lesson can be submitted; this one is %s".formatted(status));
        }
        requireNotEmpty(sentenceCount);
        this.status = DictationLessonStatus.PENDING_REVIEW;
        getReview().markSubmitted(now);
    }

    public void approve(UUID reviewerId, long sentenceCount, Instant now) {
        requirePendingReview("approved");
        requireNotEmpty(sentenceCount);
        this.status = DictationLessonStatus.PUBLISHED;
        this.publishedAt = now;
        getReview().markApproved(reviewerId, now);
    }

    public void reject(UUID reviewerId, String note, Instant now) {
        requirePendingReview("rejected");
        getReview().markRejected(reviewerId, note, now);
        this.status = DictationLessonStatus.REJECTED;
    }

    private void requirePendingReview(String verb) {
        if (status != DictationLessonStatus.PENDING_REVIEW) {
            throw new ConflictException("DICTATION_LESSON_NOT_PENDING_REVIEW",
                    "Only a lesson waiting on review can be %s; this one is %s".formatted(verb, status));
        }
    }

    private static void requireNotEmpty(long sentenceCount) {
        if (sentenceCount == 0) {
            throw new ConflictException("DICTATION_LESSON_EMPTY", "A lesson with no sentences cannot be published");
        }
    }

    public void archive() {
        if (status == DictationLessonStatus.ARCHIVED) {
            throw new ConflictException("DICTATION_LESSON_ALREADY_ARCHIVED", "This lesson is already archived");
        }
        this.status = DictationLessonStatus.ARCHIVED;
    }
}
