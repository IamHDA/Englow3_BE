package com.englow3.learning.dto.response;

import java.util.UUID;

import com.englow3.learning.dto.result.FlashcardSetSummaryResult;

public record FlashcardSetResponse(UUID id, String slug, String name, String description, String topic,
        String targetLevel, long cardCount, long dueCount, long masteredCount) {

    public static FlashcardSetResponse from(FlashcardSetSummaryResult result) {
        return new FlashcardSetResponse(result.id(), result.slug(), result.name(), result.description(), result.topic(),
                result.targetLevel(), result.cardCount(), result.dueCount(), result.masteredCount());
    }
}
