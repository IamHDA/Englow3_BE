package com.englow3.ai.speaking;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record SpeechProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("https://example.cognitiveservices.azure.com") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("en-US") String locale,
        @DefaultValue("45s") Duration readTimeout,
        @DefaultValue("10485760") long maxAudioBytes,
        @DefaultValue("10m") Duration uploadUrlTtl,
        @DefaultValue("30d") Duration retention) {
}
