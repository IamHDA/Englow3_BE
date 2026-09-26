package com.englow3.dictation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.dictation.dto.result.ContentReviewResult;

public record ContentReviewResponse(UUID id, String slug, String title, String status, long itemCount,
        Instant createdAt, Instant publishedAt, Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt,
        String reviewNote) {

    public static ContentReviewResponse from(ContentReviewResult result) {
        return new ContentReviewResponse(result.id(), result.slug(), result.title(), result.status(),
                result.itemCount(), result.createdAt(), result.publishedAt(), result.submittedForReviewAt(),
                result.reviewedByUserId(), result.reviewedAt(), result.reviewNote());
    }
}
