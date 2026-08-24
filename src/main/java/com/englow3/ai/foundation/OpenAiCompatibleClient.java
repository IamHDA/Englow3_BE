package com.englow3.ai.foundation;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
final class OpenAiCompatibleClient implements AiTextClient {

    private final RestClient restClient;
    private final AiProperties properties;

    OpenAiCompatibleClient(RestClient aiRestClient, AiProperties properties) {
        this.restClient = aiRestClient;
        this.properties = properties;
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
        ChatRequest body = new ChatRequest(request.model(),
                List.of(new Message("system", request.systemPrompt()), new Message("user", request.userPrompt())),
                request.temperature(), request.maxOutputTokens(),
                request.jsonOutput() ? Map.of("type", "json_object") : null);
        try {
            ChatResponse response = restClient.post().uri("/chat/completions").body(body).retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().getFirst().message() == null) {
                throw new AiProviderException("AI_EMPTY_RESPONSE", "The AI provider returned no completion", true);
            }
            Usage usage = response.usage() == null ? new Usage(0, 0) : response.usage();
            return new AiTextResult(response.choices().getFirst().message().content(), response.model(),
                    usage.promptTokens(), usage.completionTokens());
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            boolean retryable = status.value() == 429 || status.is5xxServerError();
            throw new AiProviderException("AI_PROVIDER_HTTP_" + status.value(), "The AI provider rejected the request",
                    retryable, ex);
        } catch (ResourceAccessException ex) {
            throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", "The AI provider is unavailable", true, ex);
        }
    }

    private record ChatRequest(String model, List<Message> messages, double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            @JsonProperty("response_format") Map<String, String> responseFormat) {
    }

    private record Message(String role, String content) {
    }

    private record ChatResponse(String model, List<Choice> choices, Usage usage) {
    }

    private record Choice(Message message) {
    }

    private record Usage(@JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens) {
    }
}
