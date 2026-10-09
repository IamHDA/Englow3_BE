package com.englow3.dictation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.dictation.dto.result.DictationLessonSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DictationLessonResponse(UUID id, String slug, String title, String topic,
        @Schema(nullable = true) String targetLevel, long sentenceCount, long completedSentenceCount,
        int totalDurationSeconds, @Schema(nullable = true) Instant lastPractisedAt,
        @Schema(nullable = true) Instant publishedAt) {

    public static DictationLessonResponse from(DictationLessonSummaryResult result) {
        return new DictationLessonResponse(result.id(), result.slug(), result.title(), result.topic(),
                result.targetLevel(), result.sentenceCount(), result.completedSentenceCount(),
                result.totalDurationSeconds(), result.lastPractisedAt(), result.publishedAt());
    }
}
