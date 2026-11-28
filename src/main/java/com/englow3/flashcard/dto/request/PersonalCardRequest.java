package com.englow3.flashcard.dto.request;

import com.englow3.flashcard.dto.command.PersonalCardCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A learner's own card: a term and its Vietnamese meaning are required, everything else is optional. */
public record PersonalCardRequest(@NotBlank @Size(max = 120) String lemma,
        @NotBlank @Size(max = 2000) String definitionVi, @Size(max = 20) String partOfSpeech,
        @Size(max = 120) String ipaUs, @Size(max = 2000) String definitionEn, @Size(max = 1000) String exampleSentence,
        @Size(max = 1000) String exampleTranslationVi, @Size(max = 1000) String mnemonicTipVi) {

    public PersonalCardCommand toCommand() {
        return new PersonalCardCommand(lemma, definitionVi, partOfSpeech, ipaUs, definitionEn, exampleSentence,
                exampleTranslationVi, mnemonicTipVi);
    }
}
