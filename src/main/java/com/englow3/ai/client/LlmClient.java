package com.englow3.ai.client;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks {@code ai_service} to generate text. The adapter holds the provider credentials; nothing here knows or cares
 * which model is behind it, which is what makes swapping one a redeploy of the adapter rather than a change in Spring.
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class LlmClient {

    private static final String GENERATE_PATH = "/internal/v1/llm/generate";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String defaultModel;

    public LlmClient(@Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.internal-api-key:}") String internalApiKey,
            @Value("${app.ai.default-model}") String defaultModel,
            @Value("${app.ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ai.read-timeout:55s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Set explicitly for the same reason as the speech client: the default is no timeout at all, and a worker
        // blocked on a provider that stopped answering holds its job RUNNING until the stall reconciler notices.
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.defaultModel = defaultModel;
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }

    /**
     * Generates one answer. Returns the adapter's JSON untouched, as the speech client does: the queue stores it and
     * the module that asked is the one that knows what to read out of it.
     *
     * @param temperature
     *            how much the model is allowed to vary. A tutor explaining a grammar rule should give the same answer
     *            twice, so callers here pass a low one.
     *
     * @throws LlmException
     *             carrying whether the request is worth repeating
     */
    public String generate(String systemPrompt, String userPrompt, double temperature, int maxOutputTokens) {
        return request(systemPrompt, userPrompt, temperature, maxOutputTokens, false);
    }

    public String generateStructured(String systemPrompt, String userPrompt, int maxOutputTokens) {
        return request(systemPrompt, userPrompt, 0.1, maxOutputTokens, true);
    }

    /** Reads the adapter's SSE; partial persistence is bounded/coalesced by the feature handler. */
    public String generateStreaming(String systemPrompt, String userPrompt, java.util.List<Turn> turns,
            double temperature, int maxOutputTokens, java.util.function.Consumer<String> partial) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(
                Map.of("model", defaultModel, "system_prompt", systemPrompt, "user_prompt", userPrompt, "temperature",
                        temperature, "max_output_tokens", maxOutputTokens));
        if (turns != null)
            body.put("messages", turns);
        if (turns != null)
            body.put("reasoning_effort", "low");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            return restClient.post().uri("/internal/v1/llm/stream").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM).body(body).exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            var failure = AiServiceFailure.decode(response.getBody(),
                                    "AI_SERVICE_HTTP_" + response.getStatusCode().value(),
                                    SpeechAssessmentClient.retryable(response.getStatusCode()));
                            throw new LlmException(failure.code(), "ai_service answered " + response.getStatusCode(),
                                    failure.retryable()).retryAfter(response.getHeaders().getFirst("Retry-After"));
                        }
                        StringBuilder text = new StringBuilder();
                        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.getBody(),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:"))
                                    continue;
                                var event = mapper.readTree(line.substring(5).strip());
                                if (event.has("error")) {
                                    var error = event.path("error");
                                    LlmException failure = new LlmException(
                                            AiServiceFailure.code(error, "AI_STREAM_FAILED"), "AI stream failed",
                                            error.path("retryable").asBoolean());
                                    if (error.path("retry_after_seconds").canConvertToInt())
                                        failure.retryAfter(error.path("retry_after_seconds").asText());
                                    throw failure;
                                }
                                if (event.has("delta")) {
                                    text.append(event.path("delta").asText());
                                    if (text.length() > 200000)
                                        throw new LlmException("TUTOR_REPLY_UNREADABLE", "Reply too large", false);
                                    partial.accept(text.toString());
                                }
                                if (event.has("result")) {
                                    if (!text.toString().equals(event.path("result").path("content").asText()))
                                        throw new LlmException("TUTOR_STREAM_INTERRUPTED", "Incomplete stream", true);
                                    return mapper.writeValueAsString(event.path("result"));
                                }
                            }
                        }
                        throw new LlmException("TUTOR_STREAM_INTERRUPTED", "Stream ended without a result", true);
                    });
        } catch (RestClientException failure) {
            throw new LlmException("TUTOR_SERVICE_UNREACHABLE", "AI stream connection failed", true);
        }
    }

    public record Turn(String role, String content) {
    }

    public String generateTurns(String systemPrompt, String userPrompt, java.util.List<Turn> turns, double temperature,
            int maxOutputTokens) {
        return request(systemPrompt, userPrompt, temperature, maxOutputTokens, false, turns);
    }

    private String request(String systemPrompt, String userPrompt, double temperature, int maxOutputTokens,
            boolean jsonOutput) {
        return request(systemPrompt, userPrompt, temperature, maxOutputTokens, jsonOutput, null);
    }

    private String request(String systemPrompt, String userPrompt, double temperature, int maxOutputTokens,
            boolean jsonOutput, java.util.List<Turn> turns) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(
                Map.of("model", defaultModel, "system_prompt", systemPrompt, "user_prompt", userPrompt, "temperature",
                        temperature, "max_output_tokens", maxOutputTokens, "json_output", jsonOutput));
        if (turns != null)
            body.put("messages", turns);
        if (turns != null)
            body.put("reasoning_effort", "low");

        try {
            return restClient.post().uri(GENERATE_PATH).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    // What matters is not "did it fail" but "would the same request work in a minute", and only the
                    // status answers that.
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        var failure = AiServiceFailure.decode(response.getBody(),
                                "AI_SERVICE_HTTP_" + response.getStatusCode().value(),
                                SpeechAssessmentClient.retryable(response.getStatusCode()));
                        throw new LlmException(failure.code(),
                                "ai_service answered %s".formatted(response.getStatusCode()), failure.retryable())
                                        .retryAfter(response.getHeaders().getFirst("Retry-After"));
                    }).body(String.class);
        } catch (RestClientException transportFailure) {
            // Nothing came back at all: a timeout, a refused connection, a DNS failure. None of them says anything
            // about the question that was asked, so all of them are worth another go.
            throw new LlmException("TUTOR_SERVICE_UNREACHABLE", transportFailure.getMessage(), true);
        }
    }

    public String defaultModel() {
        return defaultModel;
    }
}
