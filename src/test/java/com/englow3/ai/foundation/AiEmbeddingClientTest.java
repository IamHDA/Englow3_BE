package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class AiEmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesAndMapsTheEmbeddingContract() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = objectMapper.writeValueAsString(java.util.Map.of("embedding",
                Collections.nCopies(AiEmbeddingClient.DIMENSIONS, 0.125), "model", "embed-v1", "inputTokens", 4));
        server.expect(requestTo("http://ai-service/internal/v1/embeddings"))
                .andExpect(content().json("{\"text\":\"approved lesson\"}"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        AiEmbeddingClient client = new AiEmbeddingClient(builder.build(), properties(true));

        AiEmbeddingClient.Result result = client.embed("approved lesson");

        assertThat(result.embedding()).hasSize(1_024);
        assertThat(result.model()).isEqualTo("embed-v1");
        assertThat(result.inputTokens()).isEqualTo(4);
        server.verify();
    }

    @Test
    void rejectsWrongDimensionsAsRetryableInvalidResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("embedding", Collections.nCopies(10, 0.1), "model", "embed-v1", "inputTokens", 1));
        server.expect(requestTo("http://ai-service/internal/v1/embeddings"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        AiEmbeddingClient client = new AiEmbeddingClient(builder.build(), properties(true));

        assertThatThrownBy(() -> client.embed("text")).isInstanceOf(AiProviderException.class).satisfies(error -> {
            AiProviderException providerError = (AiProviderException) error;
            assertThat(providerError.code()).isEqualTo("AI_EMBEDDING_INVALID_RESPONSE");
            assertThat(providerError.retryable()).isTrue();
        });
    }

    @Test
    void classifiesProviderAndDisabledFailures() {
        AiEmbeddingClient disabled = new AiEmbeddingClient(RestClient.create("http://localhost"), properties(false));
        assertThatThrownBy(() -> disabled.embed("text")).isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertThat(((AiProviderException) error).code()).isEqualTo("AI_DISABLED"));

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        AiEmbeddingClient enabled = new AiEmbeddingClient(builder.build(), properties(true));
        assertThatThrownBy(() -> enabled.embed("text")).isInstanceOf(AiProviderException.class).satisfies(error -> {
            AiProviderException providerError = (AiProviderException) error;
            assertThat(providerError.code()).isEqualTo("AI_EMBEDDING_HTTP_429");
            assertThat(providerError.retryable()).isTrue();
        });
    }

    private AiProperties properties(boolean enabled) {
        return new AiProperties(enabled, "ai-service", "http://localhost", "key", "model", Duration.ofSeconds(1),
                Duration.ofSeconds(1), 100, 10, new AiProperties.Worker(1, Duration.ofMinutes(1)));
    }
}
