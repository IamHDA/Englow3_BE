package com.englow3.dictation.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sentence as the learner practises it.
 * <p>
 * It carries the transcript, so the screen can mark an answer the moment it is typed instead of waiting on a round trip
 * for every check. That is a product decision: the transcript is not a secret worth a network delay on each line. What
 * is kept server-side is the record - a submission is scored again from the stored transcript, and only that score is
 * stored.
 *
 * @param text
 *            what is said in the audio, for marking an answer in the browser
 * @param translationVi
 *            the Vietnamese meaning, shown with the result; null when the lesson has none
 * @param audioStartMs
 *            where this sentence begins inside its recording, for a lesson cut from one long recording. Null on both
 *            offsets means the file is this sentence and nothing else, which is what a lesson with a clip per line has
 *            always meant - so a player that ignores them keeps working on older content.
 */
public record DictationSentenceResult(UUID id, int orderNo, String text, String translationVi, String audioUrl,
        int audioDurationSeconds, int hintWordCount, String hintFirstLetters, String hintRevealWord,
        String hintPartialTranscript, Integer audioStartMs, Integer audioEndMs, BigDecimal bestAccuracyPercent) {
}
