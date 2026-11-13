package com.englow3.flashcard.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One answer, appended and never updated. {@link FlashcardReview} says where a card stands now; only these rows can say
 * what the learner actually did last week, which is what history, streaks and the progress dashboard are built from.
 */
@Entity
@Table(name = "flashcard_review_logs")
@Getter
public class FlashcardReviewLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "flashcard_id", nullable = false, updatable = false)
    private UUID flashcardId;

    @Column(name = "flashcard_set_id", nullable = false, updatable = false)
    private UUID flashcardSetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReviewRating rating;

    @Column(name = "time_spent_seconds", nullable = false, updatable = false)
    private int timeSpentSeconds;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected FlashcardReviewLog() {
    }

    public static FlashcardReviewLog of(UUID userId, UUID flashcardId, UUID flashcardSetId, ReviewRating rating,
            int timeSpentSeconds, Instant reviewedAt) {
        FlashcardReviewLog log = new FlashcardReviewLog();
        log.id = UUID.randomUUID();
        log.userId = userId;
        log.flashcardId = flashcardId;
        log.flashcardSetId = flashcardSetId;
        log.rating = rating;
        log.timeSpentSeconds = timeSpentSeconds;
        log.reviewedAt = reviewedAt;
        return log;
    }
}
