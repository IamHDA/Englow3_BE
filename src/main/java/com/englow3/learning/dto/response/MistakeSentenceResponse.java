package com.englow3.learning.dto.response;

import java.util.UUID;

import com.englow3.learning.query.DictationStatsQuery.MistakeSentence;

/**
 * One line to practise again.
 *
 * @param text
 *            the correct sentence. Present here, unlike on the practice type, because this screen is review of lines
 *            the learner has already answered - it is not a way to read the key before answering.
 * @param lastResponse
 *            the last thing they typed, so the screen can show what changed rather than just a new score
 */
public record MistakeSentenceResponse(UUID sentenceId, String text, String audioUrl, int audioDurationSeconds,
        UUID lessonId, String lessonTitle, int bestAccuracyPercent, long attemptCount, String lastResponse) {

    public static MistakeSentenceResponse from(MistakeSentence sentence, FlashcardMediaUrls media) {
        return new MistakeSentenceResponse(sentence.sentenceId(), sentence.text(),
                media.urlFor(sentence.audioObjectKey()), sentence.audioDurationSeconds(), sentence.lessonId(),
                sentence.lessonTitle(), sentence.bestAccuracyPercent(), sentence.attemptCount(),
                sentence.lastResponse());
    }
}
