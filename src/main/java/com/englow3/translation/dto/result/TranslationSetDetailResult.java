package com.englow3.translation.dto.result;

import java.util.List;
import java.util.UUID;

import com.englow3.translation.entity.TranslationAttemptStatus;

/**
 * A set as the learner practises it. The reference translations are not here: they are shown with a graded attempt,
 * after the learner has committed an answer of their own.
 */
public record TranslationSetDetailResult(TranslationSetSummaryResult set, List<Item> items) {

    /**
     * @param latestAttempt
     *            this learner's most recent attempt at the sentence; null before the first
     */
    public record Item(UUID id, int orderNo, String sourceVi, LatestAttempt latestAttempt) {
    }

    public record LatestAttempt(UUID id, TranslationAttemptStatus status, Integer score) {
    }
}
