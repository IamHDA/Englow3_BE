package com.englow3.dictation.dto.response;

import java.util.UUID;

import com.englow3.dictation.dto.result.MistakeSentenceResult;

/**
 * One line to practise again. No transcript, like the practice type. It used to carry one, on the reasoning that a
 * learner reviewing a line has already seen its answer; true, but the review screen then graded in the browser against
 * it and never told the server, so reviewed lines were never recorded and came back on the next visit. Review now
 * submits like practice does, and the answer arrives in that response - after the learner has committed one.
 *
 * @param audioStartMs
 *            the sentence's window into its recording, for a lesson cut from one passage - see
 *            {@code DictationSentenceResult}
 * @param lastResponse
 *            the last thing they typed, so the screen can show what changed rather than just a new score
 */
public record MistakeSentenceResponse(UUID sentenceId, String audioUrl, int audioDurationSeconds, Integer audioStartMs,
        Integer audioEndMs, UUID lessonId, String lessonTitle, int bestAccuracyPercent, long attemptCount,
        String lastResponse) {

    public static MistakeSentenceResponse from(MistakeSentenceResult sentence) {
        return new MistakeSentenceResponse(sentence.sentenceId(), sentence.audioUrl(), sentence.audioDurationSeconds(),
                sentence.audioStartMs(), sentence.audioEndMs(), sentence.lessonId(), sentence.lessonTitle(),
                sentence.bestAccuracyPercent(), sentence.attemptCount(), sentence.lastResponse());
    }
}
