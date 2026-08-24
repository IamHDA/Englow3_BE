package com.englow3.ai.tutor;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.englow3.ai.foundation.AiProperties;

@Component
class TutorEmbeddingClient {

    static final int DIMENSIONS = 1_024;

    private final RestClient restClient;
    private final AiProperties properties;

    TutorEmbeddingClient(RestClient aiServiceRestClient, AiProperties properties) {
        this.restClient = aiServiceRestClient;
        this.properties = properties;
    }

    Optional<List<Double>> embed(String text) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            EmbeddingResponse response = restClient.post().uri("/internal/v1/embeddings")
                    .body(new EmbeddingRequest(text)).retrieve().body(EmbeddingResponse.class);
            if (response == null || response.embedding() == null || response.embedding().size() != DIMENSIONS
                    || response.embedding().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(response.embedding()));
        } catch (RestClientException exception) {
            // Semantic retrieval degrades to lexical retrieval when embeddings are unavailable.
            return Optional.empty();
        }
    }

    private record EmbeddingRequest(String text) {
    }

    private record EmbeddingResponse(List<Double> embedding, String model, int inputTokens) {
    }
}
