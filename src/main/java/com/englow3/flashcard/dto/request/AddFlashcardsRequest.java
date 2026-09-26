package com.englow3.flashcard.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Bulk insert, because the catalogue arrives from the data pipeline in batches of hundreds and one card per request
 * would be hundreds of transactions for one logical act. Field names follow {@code data_pipeline/schemas/flashcard.py}
 * so a generated file maps across without renaming.
 */
public record AddFlashcardsRequest(@NotEmpty @Size(max = 500) List<@Valid CardRequest> cards) {

    public record CardRequest(@NotBlank @Size(max = 120) String lemma, @NotBlank @Size(max = 20) String partOfSpeech,
            @NotBlank @Size(max = 200) String senseLabel, @NotBlank @Size(max = 120) String ipaUs,
            @Size(max = 120) String ipaUk, @Size(max = 500) String audioUsObjectKey,
            @Size(max = 500) String audioUkObjectKey, @NotBlank String definitionEn, @NotBlank String definitionVi,
            @NotBlank String exampleSentence, String exampleTranslationVi, String mnemonicTipVi,
            @Pattern(regexp = "^(A1|A2|B1|B2|C1|C2)$", message = "CEFR level must be a band") String cefrLevel) {
    }
}
