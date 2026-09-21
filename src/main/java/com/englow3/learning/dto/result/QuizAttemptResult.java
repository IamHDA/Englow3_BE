package com.englow3.learning.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.learning.entity.QuizAttempt;
import com.englow3.learning.entity.QuizAttemptStatus;
import com.englow3.learning.entity.QuizQuestionType;

public record QuizAttemptResult(UUID id, UUID quizId, String quizTitle, QuizAttemptStatus status, Instant startedAt,
        Instant expiresAt, Instant submittedAt, BigDecimal score, BigDecimal maxScore, BigDecimal scorePercentage,
        Integer correctAnswerCount, int questionCount, Boolean passed, boolean resumed,
        List<QuestionReviewResult> reviews) {

    public static QuizAttemptResult started(QuizAttempt attempt, String quizTitle, boolean resumed) {
        return new QuizAttemptResult(attempt.getId(), attempt.getQuizId(), quizTitle, attempt.getStatus(),
                attempt.getStartedAt(), attempt.getExpiresAt(), attempt.getSubmittedAt(), attempt.getScore(),
                attempt.getMaxScore(), attempt.getScorePercentage(), attempt.getCorrectAnswerCount(),
                attempt.getQuestionCount(), attempt.getPassed(), resumed, List.of());
    }

    public static QuizAttemptResult scored(QuizAttempt attempt, String quizTitle, List<QuestionReviewResult> reviews) {
        return new QuizAttemptResult(attempt.getId(), attempt.getQuizId(), quizTitle, attempt.getStatus(),
                attempt.getStartedAt(), attempt.getExpiresAt(), attempt.getSubmittedAt(), attempt.getScore(),
                attempt.getMaxScore(), attempt.getScorePercentage(), attempt.getCorrectAnswerCount(),
                attempt.getQuestionCount(), attempt.getPassed(), false, reviews);
    }

    /** One question after marking: what was asked, what they said, what was right, and why. */
    public record QuestionReviewResult(UUID questionId, QuizQuestionType questionType, String prompt,
            String userAnswerText, String correctAnswerText, boolean correct, BigDecimal pointsEarned,
            int pointsPossible, String explanation) {
    }
}
