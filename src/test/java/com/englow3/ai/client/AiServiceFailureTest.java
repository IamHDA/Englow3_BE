package com.englow3.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AiServiceFailureTest {
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
