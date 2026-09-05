package com.englow3.ai.embedding;

import java.time.Instant;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class AiEmbeddingIndexDtos {

    private AiEmbeddingIndexDtos() {
    }

    record ReindexRequest(@Pattern(regexp = "EXAM_ITEM|SHADOWING_CLIP|FLASHCARD|GRAMMAR_POINT") String contentType,
            @Size(max = 200) String contentId) {
    }

    record ReindexResponse(int queued) {
    }

    record StateResponse(String contentType, String contentId, int revision, String contentHash, String status,
            int attemptCount, String provider, String model, Integer dimensions, String errorCode, Instant updatedAt,
            Instant indexedAt) {
    }
}
