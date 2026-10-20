package com.englow3.exam.api;

import java.util.UUID;

public interface SubmittedExamContextReader {
    Question read(UUID attemptId, UUID questionId);

    record Question(UUID attemptId, UUID questionId, String prompt, String passage, String instructions,
            String selectedAnswers, String correctAnswers, String explanation, boolean correct,
            java.math.BigDecimal awardedRawScore, java.math.BigDecimal maxRawScore, boolean audioNotIncluded,
            boolean imageNotIncluded) {
    }
}
