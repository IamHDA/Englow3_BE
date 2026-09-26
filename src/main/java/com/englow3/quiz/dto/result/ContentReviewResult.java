package com.englow3.quiz.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.quiz.entity.Quiz;

public record ContentReviewResult(UUID id, String slug, String title, String status, long itemCount, Instant createdAt,
        Instant publishedAt, Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt,
        String reviewNote) {

    public static ContentReviewResult of(Quiz quiz, long questionCount) {
        return new ContentReviewResult(quiz.getId(), quiz.getSlug(), quiz.getTitle(), quiz.getStatus().name(),
                questionCount, quiz.getCreatedAt(), quiz.getPublishedAt(), quiz.getReview().getSubmittedForReviewAt(),
                quiz.getReview().getReviewedByUserId(), quiz.getReview().getReviewedAt(),
                quiz.getReview().getReviewNote());
    }
}
