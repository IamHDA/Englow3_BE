package com.englow3.ai.foundation;

import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public final class AiEmbeddingClient {

    public static final int DIMENSIONS = 1_024;

    private final RestClient restClient;
    private final AiProperties properties;

    public AiEmbeddingClient(RestClient aiServiceRestClient, AiProperties properties) {
        this.restClient = aiServiceRestClient;
        this.properties = properties;
    }

    public Result embed(String text) {
        if (!properties.enabled()) {
            throw new AiProviderException("AI_DISABLED", "AI capabilities are disabled", false);
        }
        if (text == null || text.isBlank()) {
            throw new AiProviderException("AI_EMBEDDING_TEXT_EMPTY", "Embedding text cannot be empty", false);
        }
        try {
            Response response = restClient.post().uri("/internal/v1/embeddings").body(new Request(text)).retrieve()
                    .body(Response.class);
            if (response == null || response.embedding() == null || response.embedding().size() != DIMENSIONS
                    || response.embedding().stream().anyMatch(value -> value == null || !Double.isFinite(value))
                    || response.model() == null || response.model().isBlank() || response.inputTokens() < 0) {
                throw invalidResponse(null);
            }
            return new Result(List.copyOf(response.embedding()), response.model(), response.inputTokens());
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            boolean retryable = status.value() == 408 || status.value() == 425 || status.value() == 429
                    || status.is5xxServerError();
            throw new AiProviderException("AI_EMBEDDING_HTTP_" + status.value(),
                    "The AI service rejected the embedding request", retryable, ex);
        } catch (ResourceAccessException ex) {
            throw new AiProviderException("AI_SERVICE_UNAVAILABLE", "The AI service is unavailable", true, ex);
        } catch (RestClientException ex) {
            throw invalidResponse(ex);
        }
    }

    private AiProviderException invalidResponse(Throwable cause) {
        return new AiProviderException("AI_EMBEDDING_INVALID_RESPONSE", "The AI service returned an invalid embedding",
                true, cause);
    }

    public record Result(List<Double> embedding, String model, int inputTokens) {
    }

    private record Request(String text) {
    }

    private record Response(List<Double> embedding, String model, int inputTokens) {
    }
}
