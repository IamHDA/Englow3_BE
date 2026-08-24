package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class AiServiceTextClientTest {

    @Test
    void sendsTheInternalContractAndMapsItsResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service")
                .defaultHeader("X-Internal-API-Key", "internal-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/llm/generate"))
                .andExpect(header("X-Internal-API-Key", "internal-key")).andExpect(content().json("""
                        {
                          "model": "test-model",
                          "system_prompt": "system",
                          "user_prompt": "user",
                          "temperature": 0.2,
                          "max_output_tokens": 100,
                          "json_output": true
                        }
                        """)).andRespond(withSuccess("""
                        {
                          "content": "answer ok",
                          "model": "resolved-model",
                          "input_tokens": 12,
                          "output_tokens": 4
                        }
                        """, MediaType.APPLICATION_JSON));
        AiServiceTextClient client = new AiServiceTextClient(builder.build(), properties(true), new ObjectMapper());

        AiTextResult result = client.generate(new AiTextRequest("test-model", "system", "user", 0.2, 100, true));

        assertThat(result.content()).isEqualTo("answer ok");
        assertThat(result.model()).isEqualTo("resolved-model");
        assertThat(result.inputTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(4);
        server.verify();
    }

    @Test
    void refusesServiceCallsWhenAiIsDisabled() {
        AiProperties properties = properties(false);
        AiServiceTextClient client = new AiServiceTextClient(RestClient.create("http://localhost"), properties,
                new ObjectMapper());

        assertThatThrownBy(() -> client.generate(new AiTextRequest("model", "system", "user", 0.2, 100, true)))
                .isInstanceOf(AiProviderException.class).satisfies(error -> assertCode(error, "AI_DISABLED"));
    }

    @Test
    void propagatesStructuredErrorsFromTheAiService() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/llm/generate"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"AI_PROVIDER_HTTP_429","message":"Provider rate limited","retryable":true}
                        """));
        AiServiceTextClient client = new AiServiceTextClient(builder.build(), properties(true), new ObjectMapper());

        assertThatThrownBy(() -> client.generate(request())).isInstanceOf(AiProviderException.class)
                .satisfies(error -> {
                    AiProviderException providerError = (AiProviderException) error;
                    assertThat(providerError.code()).isEqualTo("AI_PROVIDER_HTTP_429");
                    assertThat(providerError.retryable()).isTrue();
                });
        server.verify();
    }

    @Test
    void hidesMalformedErrorBodiesAndMarksTransientStatusesRetryable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/llm/generate"))
                .andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT).body("provider-secret"));
        AiServiceTextClient client = new AiServiceTextClient(builder.build(), properties(true), new ObjectMapper());

        assertThatThrownBy(() -> client.generate(request())).isInstanceOf(AiProviderException.class)
                .satisfies(error -> {
                    AiProviderException providerError = (AiProviderException) error;
                    assertThat(providerError.code()).isEqualTo("AI_SERVICE_HTTP_408");
                    assertThat(providerError.retryable()).isTrue();
                    assertThat(providerError.getMessage()).doesNotContain("provider-secret");
                });
        server.verify();
    }

    @Test
    void mapsMalformedSuccessPayloadsToAStableRetryableError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/llm/generate"))
                .andRespond(withSuccess("{invalid-json", MediaType.APPLICATION_JSON));
        AiServiceTextClient client = new AiServiceTextClient(builder.build(), properties(true), new ObjectMapper());

        assertThatThrownBy(() -> client.generate(request())).isInstanceOf(AiProviderException.class)
                .satisfies(error -> {
                    AiProviderException providerError = (AiProviderException) error;
                    assertThat(providerError.code()).isEqualTo("AI_SERVICE_INVALID_RESPONSE");
                    assertThat(providerError.retryable()).isTrue();
                });
        server.verify();
    }

    @Test
    void rejectsImpossibleTokenCountersFromTheAiService() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/llm/generate")).andRespond(withSuccess("""
                {"content":"answer","model":"model","input_tokens":-1,"output_tokens":2}
                """, MediaType.APPLICATION_JSON));
        AiServiceTextClient client = new AiServiceTextClient(builder.build(), properties(true), new ObjectMapper());

        assertThatThrownBy(() -> client.generate(request())).isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertCode(error, "AI_SERVICE_INVALID_RESPONSE"));
        server.verify();
    }

    private AiTextRequest request() {
        return new AiTextRequest("model", "system", "user", 0.2, 100, true);
    }

    private void assertCode(Throwable throwable, String expected) {
        org.assertj.core.api.Assertions.assertThat(((AiProviderException) throwable).code()).isEqualTo(expected);
    }

    private AiProperties properties(boolean enabled) {
        return new AiProperties(enabled, "ai-service", "http://localhost", "internal-key", "model",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 100, 10,
                new AiProperties.Worker(1, Duration.ofMinutes(1)));
    }
}
