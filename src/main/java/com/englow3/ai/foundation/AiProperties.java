package com.englow3.ai.foundation;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record AiProperties(@DefaultValue("false") boolean enabled, @DefaultValue("ai-service") String provider,
        @DefaultValue("http://localhost:8001") String baseUrl, @DefaultValue("") String internalApiKey,
        @DefaultValue("llama-3.3-70b-versatile") String defaultModel, @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("45s") Duration readTimeout, @DefaultValue("2048") int maxOutputTokens,
        @DefaultValue("100") int dailyRequestLimit, @DefaultValue Worker worker) {

    public record Worker(@DefaultValue("5") int batchSize, @DefaultValue("5m") Duration lockTimeout) {
    }
}
