package com.englow3.dictation.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What came back from typing a sentence. This is the only shape that carries the transcript, and it exists only once
 * the learner has committed an answer.
 * <p>
 * {@code cleared} is decided here, by {@code DictationScorer.cleared}, and sent rather than left for a client to work
 * out. A screen that compared the accuracy to its own number would be a fourth definition of "done" - the review queue
 * had one, and it was 100 where everything else says 80.
 */
public record DictationSubmissionResult(UUID sentenceId, String correctText, String translationVi, String response,
        BigDecimal accuracyPercent, int correctWordCount, int totalWordCount, boolean cleared) {
}
