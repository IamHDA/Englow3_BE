package com.englow3.dictation.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.dictation.dto.result.DictationSentenceResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** No transcript on this type - see DictationService for why that is the whole point. */
public record DictationSentenceResponse(UUID id, int orderNo, String audioUrl, int audioDurationSeconds,
        int hintWordCount, @Schema(nullable = true) String hintFirstLetters,
        @Schema(nullable = true) String hintRevealWord, @Schema(nullable = true) String hintPartialTranscript,
        @Schema(nullable = true) Integer audioStartMs, @Schema(nullable = true) Integer audioEndMs,
        @Schema(nullable = true) BigDecimal bestAccuracyPercent) {

    public static DictationSentenceResponse from(DictationSentenceResult result) {
        return new DictationSentenceResponse(result.id(), result.orderNo(), result.audioUrl(),
                result.audioDurationSeconds(), result.hintWordCount(), result.hintFirstLetters(),
                result.hintRevealWord(), result.hintPartialTranscript(), result.audioStartMs(), result.audioEndMs(),
                result.bestAccuracyPercent());
    }
}
