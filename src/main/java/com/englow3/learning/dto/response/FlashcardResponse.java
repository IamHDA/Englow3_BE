package com.englow3.learning.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.learning.dto.result.FlashcardResult;
import com.englow3.learning.entity.FlashcardReviewStatus;

/**
 * Audio arrives as a signed URL, not an object key: the browser has to be able to fetch it, and a key would mean a
 * second round trip per card just to turn it into something playable.
 */
public record FlashcardResponse(UUID id, int orderNo, String lemma, String partOfSpeech, String senseLabel,
        String ipaUs, String ipaUk, String audioUsUrl, String audioUkUrl, String definitionEn, String definitionVi,
        String exampleSentence, String exampleTranslationVi, String mnemonicTipVi, String cefrLevel,
        FlashcardReviewStatus status, Instant dueAt, int lapseCount) {

    public static FlashcardResponse from(FlashcardResult result, FlashcardMediaUrls media) {
        return new FlashcardResponse(result.id(), result.orderNo(), result.lemma(), result.partOfSpeech(),
                result.senseLabel(), result.ipaUs(), result.ipaUk(), media.urlFor(result.audioUsObjectKey()),
                media.urlFor(result.audioUkObjectKey()), result.definitionEn(), result.definitionVi(),
                result.exampleSentence(), result.exampleTranslationVi(), result.mnemonicTipVi(), result.cefrLevel(),
                result.status(), result.dueAt(), result.lapseCount());
    }
}
