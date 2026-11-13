package com.englow3.translation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.translation.dto.result.TranslationSetReviewResult;
import com.englow3.translation.entity.TranslationSetStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** The same shape the other content types' review endpoints answer with, so the back office lists them alike. */
public record TranslationSetReviewResponse(UUID id, String slug, String title, TranslationSetStatus status,
        long itemCount, Instant createdAt, @Schema(nullable = true) Instant publishedAt,
        @Schema(nullable = true) Instant submittedForReviewAt, @Schema(nullable = true) UUID reviewedByUserId,
        @Schema(nullable = true) Instant reviewedAt, @Schema(nullable = true) String reviewNote) {

    public static TranslationSetReviewResponse from(TranslationSetReviewResult r) {
        return new TranslationSetReviewResponse(r.id(), r.slug(), r.title(), r.status(), r.itemCount(), r.createdAt(),
                r.publishedAt(), r.submittedForReviewAt(), r.reviewedByUserId(), r.reviewedAt(), r.reviewNote());
    }
}
