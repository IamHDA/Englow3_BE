package com.englow3.ai.client;

import java.io.InputStream;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The adapter preserves permanent provider failures even when its HTTP status is 502/503. */
final class AiServiceFailure {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiServiceFailure() {
    }

    static boolean retryable(InputStream body, boolean fallback) {
        try {
            var value = MAPPER.readTree(body).path("retryable");
            return value.isBoolean() ? value.asBoolean() : fallback;
        } catch (Exception invalid) {
            return fallback;
        }
    }
}
