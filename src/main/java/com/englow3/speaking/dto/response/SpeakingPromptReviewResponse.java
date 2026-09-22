package com.englow3.speaking.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingPromptReviewResult;
import com.englow3.speaking.entity.SpeakingPromptStatus;

public record SpeakingPromptReviewResponse(UUID id, String slug, String title, String category, String targetLevel,
        String referenceText, SpeakingPromptStatus status, Instant createdAt, Instant publishedAt,
        Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt, String reviewNote) {

    public static SpeakingPromptReviewResponse from(SpeakingPromptReviewResult result) {
        return new SpeakingPromptReviewResponse(result.id(), result.slug(), result.title(), result.category(),
                result.targetLevel(), result.referenceText(), result.status(), result.createdAt(), result.publishedAt(),
                result.submittedForReviewAt(), result.reviewedByUserId(), result.reviewedAt(), result.reviewNote());
    }
}
