package com.englow3.flashcard.dto.result;

import java.util.UUID;

import com.englow3.flashcard.entity.Flashcard;

/** A card as its owner edits it: what they wrote, without anyone's study progress. */
public record PersonalCardResult(UUID id, int orderNo, String lemma, String definitionVi, String partOfSpeech,
        String ipaUs, String definitionEn, String exampleSentence, String exampleTranslationVi, String mnemonicTipVi) {

    public static PersonalCardResult of(Flashcard card) {
        return new PersonalCardResult(card.getId(), card.getOrderNo(), card.getLemma(), card.getDefinitionVi(),
                card.getPartOfSpeech(), card.getIpaUs(), card.getDefinitionEn(), card.getExampleSentence(),
                card.getExampleTranslationVi(), card.getMnemonicTipVi());
    }
}
