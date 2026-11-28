package com.englow3.flashcard.dto.response;

import java.util.UUID;

import com.englow3.flashcard.dto.result.PersonalCardResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PersonalCardResponse(UUID id, int orderNo, String lemma, String definitionVi,
        @Schema(nullable = true) String partOfSpeech, @Schema(nullable = true) String ipaUs,
        @Schema(nullable = true) String definitionEn, @Schema(nullable = true) String exampleSentence,
        @Schema(nullable = true) String exampleTranslationVi, @Schema(nullable = true) String mnemonicTipVi) {

    public static PersonalCardResponse from(PersonalCardResult result) {
        return new PersonalCardResponse(result.id(), result.orderNo(), result.lemma(), result.definitionVi(),
                result.partOfSpeech(), result.ipaUs(), result.definitionEn(), result.exampleSentence(),
                result.exampleTranslationVi(), result.mnemonicTipVi());
    }
}
