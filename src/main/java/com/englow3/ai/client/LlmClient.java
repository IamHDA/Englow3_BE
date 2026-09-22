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
            @Value("${app.ai.read-timeout:45s}") Duration readTimeout) {
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
        Map<String, Object> body = Map.of("model", defaultModel, "system_prompt", systemPrompt, "user_prompt",
                userPrompt, "temperature", temperature, "max_output_tokens", maxOutputTokens, "json_output", false);

        try {
            return restClient.post().uri(GENERATE_PATH).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    // What matters is not "did it fail" but "would the same request work in a minute", and only the
                    // status answers that.
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new LlmException("TUTOR_GENERATION_REJECTED",
                                "ai_service answered %s".formatted(response.getStatusCode()),
                                SpeechAssessmentClient.retryable(response.getStatusCode()));
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
