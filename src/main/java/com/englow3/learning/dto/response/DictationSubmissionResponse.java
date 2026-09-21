package com.englow3.learning.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.learning.dto.result.DictationSubmissionResult;

public record DictationSubmissionResponse(UUID sentenceId, String correctText, String translationVi, String response,
        BigDecimal accuracyPercent, int correctWordCount, int totalWordCount) {

    public static DictationSubmissionResponse from(DictationSubmissionResult result) {
        return new DictationSubmissionResponse(result.sentenceId(), result.correctText(), result.translationVi(),
                result.response(), result.accuracyPercent(), result.correctWordCount(), result.totalWordCount());
    }
}
