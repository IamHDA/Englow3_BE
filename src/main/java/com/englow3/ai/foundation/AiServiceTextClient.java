package com.englow3.ai.foundation;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
final class AiServiceTextClient implements AiTextClient {

    private final RestClient restClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    AiServiceTextClient(RestClient aiServiceRestClient, AiProperties properties, ObjectMapper objectMapper) {
        this.restClient = aiServiceRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return properties.provider();
    }

    @Override
    public AiTextResult generate(AiTextRequest request) {
        if (!properties.enabled()) {
            throw new AiProviderException("AI_DISABLED", "AI capabilities are disabled", false);
        }
        try {
            GenerateResponse response = restClient.post().uri("/internal/v1/llm/generate")
                    .body(new GenerateRequest(request.model(), request.systemPrompt(), request.userPrompt(),
                            request.temperature(), request.maxOutputTokens(), request.jsonOutput()))
                    .retrieve().body(GenerateResponse.class);
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new AiProviderException("AI_EMPTY_RESPONSE", "The AI service returned no completion", true);
            }
            if (response.inputTokens() < 0 || response.outputTokens() < 0) {
                throw invalidResponse(null);
            }
            String model = response.model() == null || response.model().isBlank() ? request.model() : response.model();
            return new AiTextResult(response.content(), model, response.inputTokens(), response.outputTokens());
        } catch (RestClientResponseException ex) {
            throw serviceError(ex, "AI_SERVICE_HTTP_" + ex.getStatusCode().value(),
                    "The AI service rejected the request");
        } catch (ResourceAccessException ex) {
            throw new AiProviderException("AI_SERVICE_UNAVAILABLE", "The AI service is unavailable", true, ex);
        } catch (RestClientException ex) {
            throw invalidResponse(ex);
        }
    }

    private AiProviderException serviceError(RestClientResponseException ex, String fallbackCode,
            String fallbackMessage) {
        HttpStatusCode status = ex.getStatusCode();
        boolean fallbackRetryable = isRetryableStatus(status.value());
        try {
            ErrorResponse error = objectMapper.readValue(ex.getResponseBodyAsString(), ErrorResponse.class);
            if (error.code() != null && error.message() != null) {
                return new AiProviderException(error.code(), error.message(), error.retryable(), ex);
            }
        } catch (Exception ignored) {
            // Provider response bodies are intentionally not propagated to public clients.
        }
        return new AiProviderException(fallbackCode, fallbackMessage, fallbackRetryable, ex);
    }

    private AiProviderException invalidResponse(Throwable cause) {
        return new AiProviderException("AI_SERVICE_INVALID_RESPONSE", "The AI service returned an invalid response",
                true, cause);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    private record GenerateRequest(String model, @JsonProperty("system_prompt") String systemPrompt,
            @JsonProperty("user_prompt") String userPrompt, double temperature,
            @JsonProperty("max_output_tokens") int maxOutputTokens, @JsonProperty("json_output") boolean jsonOutput) {
    }

    private record GenerateResponse(String content, String model, @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens) {
    }

    private record ErrorResponse(String code, String message, boolean retryable) {
    }
}
