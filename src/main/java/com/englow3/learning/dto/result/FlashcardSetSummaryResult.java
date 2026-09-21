package com.englow3.learning.dto.result;

import java.util.UUID;

import com.englow3.learning.entity.FlashcardSet;

/**
 * A set as it appears in the catalogue, with the three numbers that are per-learner rather than per-set. They are
 * carried here instead of on the entity because two learners looking at the same set see different ones.
 */
public record FlashcardSetSummaryResult(UUID id, String slug, String name, String description, String topic,
        String targetLevel, long cardCount, long dueCount, long masteredCount) {

    public static FlashcardSetSummaryResult of(FlashcardSet set, long cardCount, long dueCount, long masteredCount) {
        return new FlashcardSetSummaryResult(set.getId(), set.getSlug(), set.getName(), set.getDescription(),
                set.getTopic(), set.getTargetLevel(), cardCount, dueCount, masteredCount);
    }
}
