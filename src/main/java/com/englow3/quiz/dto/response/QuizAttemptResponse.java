package com.englow3.quiz.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.quiz.dto.result.QuizAttemptResult;
import com.englow3.quiz.entity.QuizAttemptStatus;
import com.englow3.quiz.entity.QuizQuestionType;

public record QuizAttemptResponse(UUID id, UUID quizId, String quizTitle, QuizAttemptStatus status, Instant startedAt,
        Instant expiresAt, Instant submittedAt, BigDecimal score, BigDecimal maxScore, BigDecimal scorePercentage,
        Integer correctAnswerCount, int questionCount, Boolean passed, boolean resumed,
        List<QuestionReviewResponse> reviews) {

    public static QuizAttemptResponse from(QuizAttemptResult result) {
        return new QuizAttemptResponse(result.id(), result.quizId(), result.quizTitle(), result.status(),
                result.startedAt(), result.expiresAt(), result.submittedAt(), result.score(), result.maxScore(),
                result.scorePercentage(), result.correctAnswerCount(), result.questionCount(), result.passed(),
                result.resumed(), result.reviews().stream().map(QuestionReviewResponse::from).toList());
    }

    public record QuestionReviewResponse(UUID questionId, QuizQuestionType questionType, String prompt,
            String userAnswerText, String correctAnswerText, boolean correct, BigDecimal pointsEarned,
            int pointsPossible, String explanation) {

        static QuestionReviewResponse from(QuizAttemptResult.QuestionReviewResult result) {
            return new QuestionReviewResponse(result.questionId(), result.questionType(), result.prompt(),
                    result.userAnswerText(), result.correctAnswerText(), result.correct(), result.pointsEarned(),
                    result.pointsPossible(), result.explanation());
        }
    }
}
