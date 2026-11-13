package com.englow3.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AiServiceFailureTest {
    @Test
    void keepsOnlySafeTechnicalCodesAndRetryability() {
        var body = "{\"code\":\"AI_PROVIDER_HTTP_429\",\"retryable\":true,\"message\":\"private details\"}";
        var result = AiServiceFailure.decode(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                "AI_SERVICE_HTTP_502", false);
        assertThat(result.code()).isEqualTo("AI_PROVIDER_HTTP_429");
        assertThat(result.retryable()).isTrue();
        assertThat(result.toString()).doesNotContain("private details");
        var invalid = AiServiceFailure.decode(
                new ByteArrayInputStream("{\"code\":\"sk-private-key\"}".getBytes(StandardCharsets.UTF_8)),
                "AI_SERVICE_HTTP_401", false);
        assertThat(invalid.code()).isEqualTo("AI_SERVICE_HTTP_401");
    }

    @Test
    void permanentProviderFailureDoesNotConsumeRetriesJustBecauseAdapterReturned502() {
        assertThat(AiServiceFailure
                .retryable(new ByteArrayInputStream("{\"retryable\":false}".getBytes(StandardCharsets.UTF_8)), true))
                        .isFalse();
    }

    @Test
    void anHtmlColdStartPageUsesTheTransportFallback() {
        assertThat(AiServiceFailure
                .retryable(new ByteArrayInputStream("<html>Starting</html>".getBytes(StandardCharsets.UTF_8)), true))
                        .isTrue();
    }
}
