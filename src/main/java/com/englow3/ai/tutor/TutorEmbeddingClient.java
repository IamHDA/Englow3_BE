package com.englow3.ai.tutor;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import com.englow3.ai.foundation.AiEmbeddingClient;
import com.englow3.ai.foundation.AiProviderException;

@Component
class TutorEmbeddingClient {

    private final AiEmbeddingClient client;

    TutorEmbeddingClient(AiEmbeddingClient client) {
        this.client = client;
    }

    Optional<List<Double>> embed(String text) {
        try {
            return Optional.of(client.embed(text).embedding());
        } catch (AiProviderException exception) {
            // Semantic retrieval degrades to lexical retrieval when embeddings are unavailable.
            return Optional.empty();
        }
    }
}
