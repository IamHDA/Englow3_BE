package com.englow3.ai.tutor;

import java.util.List;
import java.util.UUID;

interface TutorRetrievalPort {

    RetrievalResult retrieve(UUID userId, String query, int limit);

    record RetrievalResult(List<GroundingReference> references, int candidateCount, boolean embeddingUsed,
            boolean injectionDetected) {
    }
}
