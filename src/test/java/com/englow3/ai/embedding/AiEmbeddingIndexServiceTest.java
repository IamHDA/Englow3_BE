package com.englow3.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiEmbeddingIndexServiceTest {
    @Nested
    class Success {

        @Test
        void contentHashIsStableAndSensitiveToTheExactEmbeddingText() {
            assertThat(AiEmbeddingIndexService.sha256("lesson")).hasSize(64)
                    .isEqualTo(AiEmbeddingIndexService.sha256("lesson"))
                    .isNotEqualTo(AiEmbeddingIndexService.sha256("Lesson"));
        }

    }

}
