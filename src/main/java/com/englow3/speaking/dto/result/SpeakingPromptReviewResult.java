package com.englow3.speaking.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.entity.SpeakingPromptStatus;

/**
 * A prompt as its author and reviewer see it.
 * <p>
 * Separate from {@link SpeakingPromptResult} for the reason every audience split in this codebase exists: this one
 * carries the rejection note, and a learner must never read it. Keeping them apart means there is no field on the
 * learner shape to forget to clear.
 */
public record SpeakingPromptReviewResult(UUID id, String slug, String title, String category, String targetLevel,
        String referenceText, SpeakingPromptStatus status, Instant createdAt, Instant publishedAt,
        Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt, String reviewNote) {

    public static SpeakingPromptReviewResult of(SpeakingPrompt prompt) {
        return new SpeakingPromptReviewResult(prompt.getId(), prompt.getSlug(), prompt.getTitle(), prompt.getCategory(),
                prompt.getTargetLevel(), prompt.getReferenceText(), prompt.getStatus(), prompt.getCreatedAt(),
                prompt.getPublishedAt(), prompt.getSubmittedForReviewAt(), prompt.getReviewedByUserId(),
                prompt.getReviewedAt(), prompt.getReviewNote());
    }
}
