package com.englow3.dictation.dto.result;

import java.util.UUID;

public record MistakeSentenceResult(UUID sentenceId, String audioUrl, int audioDurationSeconds, Integer audioStartMs,
        Integer audioEndMs, UUID lessonId, String lessonTitle, int bestAccuracyPercent, long attemptCount,
        String lastResponse) {
}
