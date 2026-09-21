package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptStatus;

public record ExamAttemptResult(UUID id, UUID examId, ExamAttemptStatus status, Instant startedAt, Instant expiresAt,
        Instant submittedAt, Instant scoredAt, BigDecimal rawScore, BigDecimal maxRawScore, BigDecimal scorePercentage,
        Integer correctAnswerCount, int questionCount, boolean resumed, String examTitle,
        List<QuestionReviewResult> questions) {

    /**
     * An attempt as a history row: everything except the review, which carries the answer key and is not something a
     * list of past sittings should hand out.
     */
    public static ExamAttemptResult summary(ExamAttempt attempt, String examTitle) {
        return new ExamAttemptResult(attempt.getId(), attempt.getExamId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getExpiresAt(), attempt.getSubmittedAt(), attempt.getScoredAt(), attempt.getRawScore(),
                attempt.getMaxRawScore(), attempt.getScorePercentage(), attempt.getCorrectAnswerCount(),
                attempt.getQuestionCount(), false, examTitle, List.of());
    }

    public static ExamAttemptResult started(ExamAttempt attempt, boolean resumed) {
        return from(attempt, resumed, List.of());
    }

    public static ExamAttemptResult scored(ExamAttempt attempt, List<QuestionReviewResult> questions) {
        return from(attempt, false, questions);
    }

    private static ExamAttemptResult from(ExamAttempt attempt, boolean resumed, List<QuestionReviewResult> questions) {
        return new ExamAttemptResult(attempt.getId(), attempt.getExamId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getExpiresAt(), attempt.getSubmittedAt(), attempt.getScoredAt(), attempt.getRawScore(),
                attempt.getMaxRawScore(), attempt.getScorePercentage(), attempt.getCorrectAnswerCount(),
                attempt.getQuestionCount(), resumed, null, questions);
    }

    public record QuestionReviewResult(UUID questionId, List<UUID> selectedOptionIds, List<UUID> correctOptionIds,
            boolean correct, BigDecimal awardedRawScore, String explanation, List<OptionReviewResult> options) {
    }

    public record OptionReviewResult(UUID optionId, boolean correct, String explanation) {
    }
}
