package com.englow3.flashcard.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.flashcard.entity.FlashcardSet;

public record ContentReviewResult(UUID id, String slug, String title, String status, long itemCount, Instant createdAt,
        Instant publishedAt, Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt,
        String reviewNote) {

    public static ContentReviewResult of(FlashcardSet set, long cardCount) {
        return new ContentReviewResult(set.getId(), set.getSlug(), set.getName(), set.getStatus().name(), cardCount,
                set.getCreatedAt(), set.getPublishedAt(), set.getReview().getSubmittedForReviewAt(),
                set.getReview().getReviewedByUserId(), set.getReview().getReviewedAt(),
                set.getReview().getReviewNote());
    }
}
