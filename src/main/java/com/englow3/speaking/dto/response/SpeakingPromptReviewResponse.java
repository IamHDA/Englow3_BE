package com.englow3.speaking.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingPromptReviewResult;
import com.englow3.speaking.entity.SpeakingPromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpeakingPromptReviewResponse(UUID id, String slug, String title, String category,
        @Schema(nullable = true) String targetLevel, String referenceText, SpeakingPromptStatus status,
        Instant createdAt, @Schema(nullable = true) Instant publishedAt,
        @Schema(nullable = true) Instant submittedForReviewAt, @Schema(nullable = true) UUID reviewedByUserId,
        @Schema(nullable = true) Instant reviewedAt, @Schema(nullable = true) String reviewNote) {

    public static SpeakingPromptReviewResponse from(SpeakingPromptReviewResult result) {
        return new SpeakingPromptReviewResponse(result.id(), result.slug(), result.title(), result.category(),
                result.targetLevel(), result.referenceText(), result.status(), result.createdAt(), result.publishedAt(),
                result.submittedForReviewAt(), result.reviewedByUserId(), result.reviewedAt(), result.reviewNote());
    }
}
