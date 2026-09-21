package com.englow3.learning.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.learning.entity.Flashcard;
import com.englow3.learning.entity.FlashcardReview;
import com.englow3.learning.entity.FlashcardReviewStatus;

/**
 * A card together with where this learner stands on it. {@code status} is NEW and {@code dueAt} null for a card the
 * learner has not met yet - no row is written until they answer it once, so an untouched catalogue costs no storage.
 */
public record FlashcardResult(UUID id, int orderNo, String lemma, String partOfSpeech, String senseLabel, String ipaUs,
        String ipaUk, String audioUsObjectKey, String audioUkObjectKey, String definitionEn, String definitionVi,
        String exampleSentence, String exampleTranslationVi, String mnemonicTipVi, String cefrLevel,
        FlashcardReviewStatus status, Instant dueAt, int lapseCount) {

    public static FlashcardResult of(Flashcard card, FlashcardReview review) {
        return new FlashcardResult(card.getId(), card.getOrderNo(), card.getLemma(), card.getPartOfSpeech(),
                card.getSenseLabel(), card.getIpaUs(), card.getIpaUk(), card.getAudioUsObjectKey(),
                card.getAudioUkObjectKey(), card.getDefinitionEn(), card.getDefinitionVi(), card.getExampleSentence(),
                card.getExampleTranslationVi(), card.getMnemonicTipVi(), card.getCefrLevel(),
                review == null ? FlashcardReviewStatus.NEW : review.getStatus(),
                review == null ? null : review.getDueAt(), review == null ? 0 : review.getLapseCount());
    }
}
