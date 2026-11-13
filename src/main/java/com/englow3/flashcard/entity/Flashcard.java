package com.englow3.flashcard.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One card. Field names follow {@code data_pipeline/schemas/flashcard.py} so the generated catalogue loads without a
 * translation step, except for audio: the pipeline emits a URL, this stores an object key and lets the storage client
 * sign it, the same way exam media works.
 */
@Entity
@Table(name = "flashcards")
@Getter
public class Flashcard {

    @Id
    private UUID id;

    @Column(name = "flashcard_set_id", nullable = false, updatable = false)
    private UUID flashcardSetId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String lemma;

    @Column(name = "part_of_speech", nullable = false)
    private String partOfSpeech;

    @Column(name = "sense_label", nullable = false)
    private String senseLabel;

    @Column(name = "ipa_us", nullable = false)
    private String ipaUs;

    @Column(name = "ipa_uk")
    private String ipaUk;

    @Column(name = "audio_us_object_key")
    private String audioUsObjectKey;

    @Column(name = "audio_uk_object_key")
    private String audioUkObjectKey;

    @Column(name = "definition_en", nullable = false)
    private String definitionEn;

    @Column(name = "definition_vi", nullable = false)
    private String definitionVi;

    @Column(name = "example_sentence", nullable = false)
    private String exampleSentence;

    @Column(name = "example_translation_vi")
    private String exampleTranslationVi;

    @Column(name = "mnemonic_tip_vi")
    private String mnemonicTipVi;

    @Column(name = "cefr_level")
    private String cefrLevel;

    protected Flashcard() {
    }

    /**
     * Position is assigned by the caller rather than derived here: a batch is inserted in one go, and having each card
     * ask the table where it belongs would be one query per card for a number the caller already knows.
     */
    public static Flashcard of(UUID flashcardSetId, int orderNo, String lemma, String partOfSpeech, String senseLabel,
            String ipaUs, String ipaUk, String audioUsObjectKey, String audioUkObjectKey, String definitionEn,
            String definitionVi, String exampleSentence, String exampleTranslationVi, String mnemonicTipVi,
            String cefrLevel) {
        Flashcard card = new Flashcard();
        card.id = UUID.randomUUID();
        card.flashcardSetId = flashcardSetId;
        card.orderNo = orderNo;
        card.lemma = lemma;
        card.partOfSpeech = partOfSpeech;
        card.senseLabel = senseLabel;
        card.ipaUs = ipaUs;
        card.ipaUk = ipaUk;
        card.audioUsObjectKey = audioUsObjectKey;
        card.audioUkObjectKey = audioUkObjectKey;
        card.definitionEn = definitionEn;
        card.definitionVi = definitionVi;
        card.exampleSentence = exampleSentence;
        card.exampleTranslationVi = exampleTranslationVi;
        card.mnemonicTipVi = mnemonicTipVi;
        card.cefrLevel = cefrLevel;
        return card;
    }
}
