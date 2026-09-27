package com.englow3.dictation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.dictation.dto.result.ContentReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ContentReviewResponse(UUID id, String slug, String title, String status, long itemCount,
        Instant createdAt, @Schema(nullable = true) Instant publishedAt,
        @Schema(nullable = true) Instant submittedForReviewAt, @Schema(nullable = true) UUID reviewedByUserId,
        @Schema(nullable = true) Instant reviewedAt, @Schema(nullable = true) String reviewNote) {

    public static ContentReviewResponse from(ContentReviewResult result) {
        return new ContentReviewResponse(result.id(), result.slug(), result.title(), result.status(),
                result.itemCount(), result.createdAt(), result.publishedAt(), result.submittedForReviewAt(),
                result.reviewedByUserId(), result.reviewedAt(), result.reviewNote());
    }
}
