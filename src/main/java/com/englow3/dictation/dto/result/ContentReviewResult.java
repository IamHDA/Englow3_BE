package com.englow3.dictation.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.dictation.entity.DictationLesson;

public record ContentReviewResult(UUID id, String slug, String title, String status, long itemCount, Instant createdAt,
        Instant publishedAt, Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt,
        String reviewNote) {

    public static ContentReviewResult of(DictationLesson lesson, long sentenceCount) {
        return new ContentReviewResult(lesson.getId(), lesson.getSlug(), lesson.getTitle(), lesson.getStatus().name(),
                sentenceCount, lesson.getCreatedAt(), lesson.getPublishedAt(),
                lesson.getReview().getSubmittedForReviewAt(), lesson.getReview().getReviewedByUserId(),
                lesson.getReview().getReviewedAt(), lesson.getReview().getReviewNote());
    }
}
