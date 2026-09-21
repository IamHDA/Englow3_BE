package com.englow3.learning.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.learning.dto.result.FlashcardReviewResult;
import com.englow3.learning.entity.FlashcardReviewStatus;

public record FlashcardReviewResponse(UUID flashcardId, FlashcardReviewStatus status, int repetitions, int intervalDays,
        Instant dueAt, int lapseCount) {

    public static FlashcardReviewResponse from(FlashcardReviewResult result) {
        return new FlashcardReviewResponse(result.flashcardId(), result.status(), result.repetitions(),
                result.intervalDays(), result.dueAt(), result.lapseCount());
    }
}
