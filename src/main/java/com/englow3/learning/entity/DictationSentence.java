package com.englow3.learning.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One line to transcribe. {@code text} is the answer, so nothing that renders a practice screen may read it - see
 * {@code DictationService} for where the boundary sits.
 */
@Entity
@Table(name = "dictation_sentences")
@Getter
public class DictationSentence {

    @Id
    private UUID id;

    @Column(name = "dictation_lesson_id", nullable = false, updatable = false)
    private UUID dictationLessonId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String text;

    @Column(name = "translation_vi")
    private String translationVi;

    @Column(name = "audio_object_key", nullable = false)
    private String audioObjectKey;

    @Column(name = "audio_duration_seconds", nullable = false)
    private int audioDurationSeconds;

    @Column(name = "hint_word_count", nullable = false)
    private int hintWordCount;

    @Column(name = "hint_first_letters")
    private String hintFirstLetters;

    @Column(name = "hint_reveal_word")
    private String hintRevealWord;

    @Column(name = "hint_partial_transcript")
    private String hintPartialTranscript;

    protected DictationSentence() {
    }

    /**
     * {@code hintWordCount} is passed in rather than counted here: the count has to agree with how the scorer splits
     * words, and the scorer owns that rule. Counting it a second time in this class is how the two would drift.
     */
    public static DictationSentence of(UUID lessonId, int orderNo, String text, String translationVi,
            String audioObjectKey, int audioDurationSeconds, int hintWordCount, String firstLetters, String revealWord,
            String partialTranscript) {
        DictationSentence sentence = new DictationSentence();
        sentence.id = UUID.randomUUID();
        sentence.dictationLessonId = lessonId;
        sentence.orderNo = orderNo;
        sentence.text = text;
        sentence.translationVi = translationVi;
        sentence.audioObjectKey = audioObjectKey;
        sentence.audioDurationSeconds = audioDurationSeconds;
        sentence.hintWordCount = hintWordCount;
        sentence.hintFirstLetters = firstLetters;
        sentence.hintRevealWord = revealWord;
        sentence.hintPartialTranscript = partialTranscript;
        return sentence;
    }
}
