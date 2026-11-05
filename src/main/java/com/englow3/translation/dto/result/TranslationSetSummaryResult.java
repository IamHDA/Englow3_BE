package com.englow3.translation.dto.result;

import java.util.UUID;

import com.englow3.translation.entity.TranslationSet;

/**
 * A published set in the learner's library.
 *
 * @param gradedCount
 *            sentences this learner has a graded attempt at
 */
public record TranslationSetSummaryResult(UUID id, String slug, String title, String topic, String targetLevel,
        String description, long itemCount, long gradedCount) {

    public static TranslationSetSummaryResult of(TranslationSet set, long itemCount, long gradedCount) {
        return new TranslationSetSummaryResult(set.getId(), set.getSlug(), set.getTitle(), set.getTopic(),
                set.getTargetLevel(), set.getDescription(), itemCount, gradedCount);
    }
}
