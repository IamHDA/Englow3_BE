package com.englow3.dictation.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.dictation.entity.DictationLesson;

/** A lesson as the library lists it. Progress and the last practice are per learner. */
public record DictationLessonSummaryResult(UUID id, String slug, String title, String topic, String targetLevel,
        long sentenceCount, long completedSentenceCount, int totalDurationSeconds, Instant lastPractisedAt) {

    public static DictationLessonSummaryResult of(DictationLesson lesson, long sentenceCount,
            long completedSentenceCount, int totalDurationSeconds, Instant lastPractisedAt) {
        return new DictationLessonSummaryResult(lesson.getId(), lesson.getSlug(), lesson.getTitle(), lesson.getTopic(),
                lesson.getTargetLevel(), sentenceCount, completedSentenceCount, totalDurationSeconds, lastPractisedAt);
    }
}
