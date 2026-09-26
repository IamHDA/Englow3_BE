package com.englow3.learning.dto.result;

import java.util.UUID;

public record MistakeSentenceResult(UUID sentenceId, String audioUrl, int audioDurationSeconds, Integer audioStartMs,
        Integer audioEndMs, UUID lessonId, String lessonTitle, int bestAccuracyPercent, long attemptCount,
        String lastResponse) {
}
