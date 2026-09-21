package com.englow3.learning.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What came back from typing a sentence. This is the only shape that carries the transcript, and it exists only once
 * the learner has committed an answer.
 */
public record DictationSubmissionResult(UUID sentenceId, String correctText, String translationVi, String response,
        BigDecimal accuracyPercent, int correctWordCount, int totalWordCount) {
}
