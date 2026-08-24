package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleClientTest {

    @Test
    void refusesProviderCallsWhenAiIsDisabled() {
        AiProperties properties = new AiProperties(false, "openai-compatible", "http://localhost", "", "model",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 100, 10,
                new AiProperties.Worker(Duration.ofSeconds(1), 1, Duration.ofMinutes(1)));
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(RestClient.create("http://localhost"), properties);

        assertThatThrownBy(() -> client.generate(new AiTextRequest("model", "system", "user", 0.2, 100, true)))
                .isInstanceOf(AiProviderException.class).satisfies(error -> assertCode(error, "AI_DISABLED"));
    }

    private void assertCode(Throwable throwable, String expected) {
        org.assertj.core.api.Assertions.assertThat(((AiProviderException) throwable).code()).isEqualTo(expected);
    }
}
