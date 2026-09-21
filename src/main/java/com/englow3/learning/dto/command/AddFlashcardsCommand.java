package com.englow3.learning.dto.command;

import java.util.List;
import java.util.UUID;

public record AddFlashcardsCommand(UUID flashcardSetId, List<NewCard> cards) {

    public record NewCard(String lemma, String partOfSpeech, String senseLabel, String ipaUs, String ipaUk,
            String audioUsObjectKey, String audioUkObjectKey, String definitionEn, String definitionVi,
            String exampleSentence, String exampleTranslationVi, String mnemonicTipVi, String cefrLevel) {
    }
}
