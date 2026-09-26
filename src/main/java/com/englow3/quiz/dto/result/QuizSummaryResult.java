package com.englow3.quiz.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.quiz.entity.Quiz;

/** A quiz as the catalogue lists it. {@code bestScorePercent} is per learner and null until they have finished one. */
public record QuizSummaryResult(UUID id, String slug, String title, String description, String category,
        String targetLevel, int timeLimitSeconds, short passingScorePercent, long questionCount,
        BigDecimal bestScorePercent, int attemptCount) {

    public static QuizSummaryResult of(Quiz quiz, long questionCount, BigDecimal bestScorePercent, int attemptCount) {
        return new QuizSummaryResult(quiz.getId(), quiz.getSlug(), quiz.getTitle(), quiz.getDescription(),
                quiz.getCategory(), quiz.getTargetLevel(), quiz.getTimeLimitSeconds(), quiz.getPassingScorePercent(),
                questionCount, bestScorePercent, attemptCount);
    }
}
