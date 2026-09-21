package com.englow3.learning.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.learning.dto.result.DictationSentenceResult;

/** No transcript on this type - see DictationService for why that is the whole point. */
public record DictationSentenceResponse(UUID id, int orderNo, String audioUrl, int audioDurationSeconds,
        int hintWordCount, String hintFirstLetters, String hintRevealWord, String hintPartialTranscript,
        BigDecimal bestAccuracyPercent) {

    public static DictationSentenceResponse from(DictationSentenceResult result, FlashcardMediaUrls media) {
        return new DictationSentenceResponse(result.id(), result.orderNo(), media.urlFor(result.audioObjectKey()),
                result.audioDurationSeconds(), result.hintWordCount(), result.hintFirstLetters(),
                result.hintRevealWord(), result.hintPartialTranscript(), result.bestAccuracyPercent());
    }
}
