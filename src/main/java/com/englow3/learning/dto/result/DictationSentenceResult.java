package com.englow3.learning.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sentence as the learner practises it. There is no transcript on this type - the answer only exists on
 * {@link DictationSubmissionResult}, which is produced after they have typed theirs.
 */
/**
 * @param audioStartMs
 *            where this sentence begins inside its recording, for a lesson cut from one long recording. Null on both
 *            offsets means the file is this sentence and nothing else, which is what a lesson with a clip per line has
 *            always meant - so a player that ignores them keeps working on older content.
 */
public record DictationSentenceResult(UUID id, int orderNo, String audioUrl, int audioDurationSeconds,
        int hintWordCount, String hintFirstLetters, String hintRevealWord, String hintPartialTranscript,
        Integer audioStartMs, Integer audioEndMs, BigDecimal bestAccuracyPercent) {
}
