package com.englow3.flashcard.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.flashcard.dto.result.FlashcardResult;
import com.englow3.flashcard.entity.FlashcardReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Audio arrives as a signed URL, not an object key: the browser has to be able to fetch it, and a key would mean a
 * second round trip per card just to turn it into something playable.
 */
public record FlashcardResponse(UUID id, int orderNo, String lemma, String partOfSpeech, String senseLabel,
        String ipaUs, @Schema(nullable = true) String ipaUk, @Schema(nullable = true) String audioUsUrl,
        @Schema(nullable = true) String audioUkUrl, String definitionEn, String definitionVi, String exampleSentence,
        @Schema(nullable = true) String exampleTranslationVi, @Schema(nullable = true) String mnemonicTipVi,
        @Schema(nullable = true) String cefrLevel, FlashcardReviewStatus status, @Schema(nullable = true) Instant dueAt,
        int lapseCount) {

    public static FlashcardResponse from(FlashcardResult result) {
        return new FlashcardResponse(result.id(), result.orderNo(), result.lemma(), result.partOfSpeech(),
                result.senseLabel(), result.ipaUs(), result.ipaUk(), result.audioUsUrl(), result.audioUkUrl(),
                result.definitionEn(), result.definitionVi(), result.exampleSentence(), result.exampleTranslationVi(),
                result.mnemonicTipVi(), result.cefrLevel(), result.status(), result.dueAt(), result.lapseCount());
    }
}
