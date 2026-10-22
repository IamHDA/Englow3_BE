package com.englow3.dictation.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.dictation.dto.result.DictationSentenceResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The transcript is on this type so the screen can mark an answer at once; the score that is stored is still the
 * server's.
 */
public record DictationSentenceResponse(UUID id, int orderNo, String text,
        @Schema(nullable = true) String translationVi, String audioUrl, int audioDurationSeconds, int hintWordCount,
        @Schema(nullable = true) String hintFirstLetters, @Schema(nullable = true) String hintRevealWord,
        @Schema(nullable = true) String hintPartialTranscript, @Schema(nullable = true) Integer audioStartMs,
        @Schema(nullable = true) Integer audioEndMs, @Schema(nullable = true) BigDecimal bestAccuracyPercent) {

    public static DictationSentenceResponse from(DictationSentenceResult result) {
        return new DictationSentenceResponse(result.id(), result.orderNo(), result.text(), result.translationVi(),
                result.audioUrl(), result.audioDurationSeconds(), result.hintWordCount(), result.hintFirstLetters(),
                result.hintRevealWord(), result.hintPartialTranscript(), result.audioStartMs(), result.audioEndMs(),
                result.bestAccuracyPercent());
    }
}
