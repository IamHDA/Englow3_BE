package com.englow3.dictation.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.dictation.dto.result.DictationSubmissionResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DictationSubmissionResponse(UUID sentenceId, String correctText,
        @Schema(nullable = true) String translationVi, String response, BigDecimal accuracyPercent,
        int correctWordCount, int totalWordCount, boolean cleared) {

    public static DictationSubmissionResponse from(DictationSubmissionResult result) {
        return new DictationSubmissionResponse(result.sentenceId(), result.correctText(), result.translationVi(),
                result.response(), result.accuracyPercent(), result.correctWordCount(), result.totalWordCount(),
                result.cleared());
    }
}
