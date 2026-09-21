package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.entity.ExamAttemptStatus;

public record ExamAttemptResponse(UUID id, UUID examId, ExamAttemptStatus status, Instant startedAt, Instant expiresAt,
        Instant submittedAt, Instant scoredAt, BigDecimal rawScore, BigDecimal maxRawScore, BigDecimal scorePercentage,
        Integer correctAnswerCount, int questionCount, boolean resumed, List<QuestionReviewResponse> questions) {

    public static ExamAttemptResponse from(ExamAttemptResult result) {
        return new ExamAttemptResponse(result.id(), result.examId(), result.status(), result.startedAt(),
                result.expiresAt(), result.submittedAt(), result.scoredAt(), result.rawScore(), result.maxRawScore(),
                result.scorePercentage(), result.correctAnswerCount(), result.questionCount(), result.resumed(),
                result.questions().stream().map(QuestionReviewResponse::from).toList());
    }

    public record QuestionReviewResponse(UUID questionId, List<UUID> selectedOptionIds, List<UUID> correctOptionIds,
            boolean correct, BigDecimal awardedRawScore, String explanation, List<OptionReviewResponse> options) {

        static QuestionReviewResponse from(ExamAttemptResult.QuestionReviewResult result) {
            return new QuestionReviewResponse(result.questionId(), result.selectedOptionIds(),
                    result.correctOptionIds(), result.correct(), result.awardedRawScore(), result.explanation(),
                    result.options().stream().map(OptionReviewResponse::from).toList());
        }
    }

    public record OptionReviewResponse(UUID optionId, boolean correct, String explanation) {
        static OptionReviewResponse from(ExamAttemptResult.OptionReviewResult result) {
            return new OptionReviewResponse(result.optionId(), result.correct(), result.explanation());
        }
    }
}
