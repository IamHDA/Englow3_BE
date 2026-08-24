package com.englow3.ai.foundation;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record AiProperties(@DefaultValue("false") boolean enabled, @DefaultValue("openai-compatible") String provider,
        @DefaultValue("https://api.groq.com/openai/v1") String baseUrl, @DefaultValue("") String apiKey,
        @DefaultValue("llama-3.3-70b-versatile") String defaultModel, @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("45s") Duration readTimeout, @DefaultValue("2048") int maxOutputTokens,
        @DefaultValue("100") int dailyRequestLimit, @DefaultValue Worker worker) {

    public record Worker(@DefaultValue("2s") Duration fixedDelay, @DefaultValue("5") int batchSize,
            @DefaultValue("5m") Duration lockTimeout) {
    }
}
