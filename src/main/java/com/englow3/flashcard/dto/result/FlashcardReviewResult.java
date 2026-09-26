package com.englow3.flashcard.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.flashcard.entity.FlashcardReview;
import com.englow3.flashcard.entity.FlashcardReviewStatus;

/** What answering a card changed: where it now sits and when it comes back. */
public record FlashcardReviewResult(UUID flashcardId, FlashcardReviewStatus status, int repetitions, int intervalDays,
        Instant dueAt, int lapseCount) {

    public static FlashcardReviewResult of(FlashcardReview review) {
        return new FlashcardReviewResult(review.getFlashcardId(), review.getStatus(), review.getRepetitions(),
                review.getIntervalDays(), review.getDueAt(), review.getLapseCount());
    }
}
