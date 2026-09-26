package com.englow3.dictation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.dictation.dto.result.DictationLessonSummaryResult;

public record DictationLessonResponse(UUID id, String slug, String title, String topic, String targetLevel,
        long sentenceCount, long completedSentenceCount, int totalDurationSeconds, Instant lastPractisedAt) {

    public static DictationLessonResponse from(DictationLessonSummaryResult result) {
        return new DictationLessonResponse(result.id(), result.slug(), result.title(), result.topic(),
                result.targetLevel(), result.sentenceCount(), result.completedSentenceCount(),
                result.totalDurationSeconds(), result.lastPractisedAt());
    }
}
