package com.englow3.ai.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The adapter's content generation endpoints (ai_service/app/generation). Bodies go in and come back as JSON strings:
 * the generation module knows what is in them; this class knows only how to reach the adapter and whether a failure is
 * worth repeating.
 * <p>
 * The read timeout stays under the worker's run limit (140s by default), so a step that hangs fails as a timeout the
 * queue can retry instead of a job reclaimed as stalled.
 */
@Component
public class GenerationClient {

    private static final String BASE = "/internal/v1/generation/ielts";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    public GenerationClient(@Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.internal-api-key:}") String internalApiKey,
            @Value("${app.ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ai.generation-read-timeout:125s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeader(INTERNAL_KEY_HEADER, internalApiKey).build();
    }

    /** Writes and checks one component. */
    public String step(String body) {
        return post("/step", body);
    }

    /** Builds the paper, the tasks, the Task 1 chart and each Listening part's lines with voices. */
    public String assemble(String body) {
        return post("/assemble", body);
    }

    /** Records one Listening part. */
    public String audio(String body) {
        return post("/audio", body);
    }

    private String post(String path, String body) {
        try {
            return restClient.post().uri(BASE + path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        var failure = AiServiceFailure.decode(response.getBody(),
                                "AI_SERVICE_HTTP_" + response.getStatusCode().value(),
                                SpeechAssessmentClient.retryable(response.getStatusCode()));
                        throw new LlmException(failure.code(),
                                "ai_service answered %s".formatted(response.getStatusCode()), failure.retryable())
                                        .retryAfter(response.getHeaders().getFirst("Retry-After"));
                    }).body(String.class);
        } catch (RestClientException transportFailure) {
            throw new LlmException("AI_GENERATION_UNREACHABLE", transportFailure.getMessage(), true);
        }
    }
}
