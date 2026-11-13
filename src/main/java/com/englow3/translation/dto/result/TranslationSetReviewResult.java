package com.englow3.translation.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.translation.entity.TranslationSet;
import com.englow3.translation.entity.TranslationSetStatus;

/**
 * A set as its author and reviewer see it. Carries the rejection note, which is why it is never the learner shape.
 */
public record TranslationSetReviewResult(UUID id, String slug, String title, TranslationSetStatus status,
        long itemCount, Instant createdAt, Instant publishedAt, Instant submittedForReviewAt, UUID reviewedByUserId,
        Instant reviewedAt, String reviewNote) {

    public static TranslationSetReviewResult of(TranslationSet set, long itemCount) {
        return new TranslationSetReviewResult(set.getId(), set.getSlug(), set.getTitle(), set.getStatus(), itemCount,
                set.getCreatedAt(), set.getPublishedAt(), set.getSubmittedForReviewAt(), set.getReviewedByUserId(),
                set.getReviewedAt(), set.getReviewNote());
    }
}
