package com.englow3.learning.entity;

import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

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

    /**
     * Where this sentence sits inside {@code audioObjectKey}, for a lesson cut from one long recording.
     * <p>
     * Null on both means the file is this sentence and nothing else, which is what an author uploading one clip per
     * line produces - and what every row written before these columns existed means.
     */
    @Column(name = "audio_start_ms")
    private Integer audioStartMs;

    @Column(name = "audio_end_ms")
    private Integer audioEndMs;

    protected DictationSentence() {
    }

    /**
     * {@code hintWordCount} is passed in rather than counted here: the count has to agree with how the scorer splits
     * words, and the scorer owns that rule. Counting it a second time in this class is how the two would drift.
     */
    public static DictationSentence of(UUID lessonId, int orderNo, String text, String translationVi,
            String audioObjectKey, int audioDurationSeconds, int hintWordCount, String firstLetters, String revealWord,
            String partialTranscript) {
        return of(lessonId, orderNo, text, translationVi, audioObjectKey, audioDurationSeconds, hintWordCount,
                firstLetters, revealWord, partialTranscript, null, null);
    }

    /**
     * The same, for a sentence that is a window inside a longer recording.
     * <p>
     * Both offsets or neither: half a window is not a window, and one of the two alone would leave the player guessing
     * where the sentence ends.
     */
    public static DictationSentence of(UUID lessonId, int orderNo, String text, String translationVi,
            String audioObjectKey, int audioDurationSeconds, int hintWordCount, String firstLetters, String revealWord,
            String partialTranscript, Integer audioStartMs, Integer audioEndMs) {
        requireWindow(audioStartMs, audioEndMs);

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
        sentence.audioStartMs = audioStartMs;
        sentence.audioEndMs = audioEndMs;
        return sentence;
    }

    /** Mirrors the database constraint, so the refusal is a domain error rather than a driver one. */
    private static void requireWindow(Integer startMs, Integer endMs) {
        if (startMs == null && endMs == null) {
            return;
        }
        if (startMs == null || endMs == null || startMs < 0 || endMs <= startMs) {
            throw new BadRequestException("DICTATION_AUDIO_WINDOW_INVALID",
                    "A sentence's audio window needs a start and a later end");
        }
    }
}
