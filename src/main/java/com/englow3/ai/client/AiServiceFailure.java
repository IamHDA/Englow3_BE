package com.englow3.ai.client;

import java.io.InputStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The adapter preserves permanent provider failures even when its HTTP status is 502/503. */
final class AiServiceFailure {
    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private AiServiceFailure() {
    }

    static boolean retryable(InputStream body, boolean fallback) {
        return decode(body, "AI_SERVICE_FAILURE", fallback).retryable();
    }

    record Failure(String code, boolean retryable) {
    }

    static String code(tools.jackson.databind.JsonNode node, String fallback) {
        String code = node.path("code").asString("");
        return code.matches("(?:AI|SPEECH|LLM)_[A-Z0-9_]{1,76}") ? code : fallback;
    }

    static Failure decode(InputStream body, String fallbackCode, boolean fallback) {
        try {
            byte[] bytes = body.readNBytes(16385);
            if (bytes.length > 16384)
                return new Failure(fallbackCode, fallback);
            var node = MAPPER.readTree(bytes);
            var value = node.path("retryable");
            return new Failure(code(node, fallbackCode), value.isBoolean() ? value.asBoolean(false) : fallback);
        } catch (Exception invalid) {
            return new Failure(fallbackCode, fallback);
        }
    }
}
