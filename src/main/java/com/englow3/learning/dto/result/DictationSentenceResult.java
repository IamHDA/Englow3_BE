package com.englow3.learning.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sentence as the learner practises it. There is no transcript on this type - the answer only exists on
 * {@link DictationSubmissionResult}, which is produced after they have typed theirs.
 */
public record DictationSentenceResult(UUID id, int orderNo, String audioObjectKey, int audioDurationSeconds,
        int hintWordCount, String hintFirstLetters, String hintRevealWord, String hintPartialTranscript,
        BigDecimal bestAccuracyPercent) {
}
