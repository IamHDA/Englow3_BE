package com.englow3.quiz.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The paper plus the attempt it belongs to - a quiz is only ever delivered through an open attempt. */
public record QuizPaperResult(UUID attemptId, UUID quizId, String title, String description, int timeLimitSeconds,
        Instant expiresAt, List<QuizQuestionResult> questions) {
}
