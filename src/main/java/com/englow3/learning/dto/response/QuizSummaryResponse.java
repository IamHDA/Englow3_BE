package com.englow3.learning.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.learning.dto.result.QuizSummaryResult;

public record QuizSummaryResponse(UUID id, String slug, String title, String description, String category,
        String targetLevel, int timeLimitSeconds, short passingScorePercent, long questionCount,
        BigDecimal bestScorePercent, int attemptCount) {

    public static QuizSummaryResponse from(QuizSummaryResult result) {
        return new QuizSummaryResponse(result.id(), result.slug(), result.title(), result.description(),
                result.category(), result.targetLevel(), result.timeLimitSeconds(), result.passingScorePercent(),
                result.questionCount(), result.bestScorePercent(), result.attemptCount());
    }
}
