package com.englow3.flashcard.entity;

import java.math.BigDecimal;
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
 * Where SM-2 has got to for one learner and one card. The scheduling arithmetic is not here: it lives in
 * {@link com.englow3.flashcard.helper.FlashcardSrs}, which is a pure function of the current state and the rating. This
 * class holds the state and applies the answer it is handed, so the algorithm can be tested without a database and
 * changed without touching persistence.
 */
@Entity
@Table(name = "flashcard_reviews")
@Getter
public class FlashcardReview {

    /** SM-2's starting ease. Every new card begins here regardless of how hard the word looks. */
    public static final BigDecimal INITIAL_EASE_FACTOR = new BigDecimal("2.50");

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "flashcard_id", nullable = false, updatable = false)
    private UUID flashcardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlashcardReviewStatus status;

    @Column(nullable = false)
    private short repetitions;

    @Column(name = "ease_factor", nullable = false)
    private BigDecimal easeFactor;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "lapse_count", nullable = false)
    private int lapseCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_rating")
    private ReviewRating lastRating;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    protected FlashcardReview() {
    }

    /**
     * A card the learner has never seen. Due immediately: an unseen card is owed now, not at some future date, and that
     * is what puts it into the first session that asks for what is due.
     */
    public static FlashcardReview unseen(UUID userId, UUID flashcardId, Instant now) {
        FlashcardReview review = new FlashcardReview();
        review.id = UUID.randomUUID();
        review.userId = userId;
        review.flashcardId = flashcardId;
        review.status = FlashcardReviewStatus.NEW;
        review.repetitions = 0;
        review.easeFactor = INITIAL_EASE_FACTOR;
        review.intervalDays = 0;
        review.dueAt = now;
        review.lapseCount = 0;
        return review;
    }

    /**
     * Applies one answer. A lapse is counted only for a card that had been learned - failing a card that was still
     * being learned is ordinary progress, not a relapse, and counting it would make the difficulty statistics lie.
     */
    public void applySchedule(ReviewRating rating, FlashcardSchedule schedule, Instant now) {
        // A card that had been learned - under review or already mastered - and is now forgotten is a lapse.
        // Counting REVIEW only left out the mastered card that slipped, the very one the "difficult" list is for.
        if (rating.isFailure()
                && (status == FlashcardReviewStatus.REVIEW || status == FlashcardReviewStatus.MASTERED)) {
            lapseCount++;
        }
        this.repetitions = schedule.repetitions();
        this.easeFactor = schedule.easeFactor();
        this.intervalDays = schedule.intervalDays();
        this.dueAt = schedule.dueAt();
        this.status = schedule.status();
        this.lastRating = rating;
        this.lastReviewedAt = now;
    }

    /** The outcome {@code FlashcardSrs} computes, kept beside the entity it is applied to. */
    public record FlashcardSchedule(short repetitions, BigDecimal easeFactor, int intervalDays, Instant dueAt,
            FlashcardReviewStatus status) {
    }
}
