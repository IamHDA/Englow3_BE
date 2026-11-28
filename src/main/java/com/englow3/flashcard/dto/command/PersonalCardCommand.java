package com.englow3.flashcard.dto.command;

/**
 * What a learner can write on their own card. Text arrives trimmed, and a blank optional field arrives as null, so an
 * emptied field is stored as "not given" rather than as an empty string the card would then show.
 */
public record PersonalCardCommand(String lemma, String definitionVi, String partOfSpeech, String ipaUs,
        String definitionEn, String exampleSentence, String exampleTranslationVi, String mnemonicTipVi) {

    public PersonalCardCommand {
        lemma = lemma == null ? null : lemma.trim();
        definitionVi = definitionVi == null ? null : definitionVi.trim();
        partOfSpeech = blankToNull(partOfSpeech);
        ipaUs = blankToNull(ipaUs);
        definitionEn = blankToNull(definitionEn);
        exampleSentence = blankToNull(exampleSentence);
        exampleTranslationVi = blankToNull(exampleTranslationVi);
        mnemonicTipVi = blankToNull(mnemonicTipVi);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
