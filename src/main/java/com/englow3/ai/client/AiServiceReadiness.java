package com.englow3.ai.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A sleeping adapter is deferred without spending the provider retry budget. */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class AiServiceReadiness {
    private final RestClient client;

    public AiServiceReadiness(@Value("${app.ai.base-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public boolean isLive() {
        try {
            String result = client.get().uri("/health/live").retrieve().body(String.class);
            return "up".equals(new ObjectMapper().readTree(result).path("status").asText());
        } catch (Exception unavailable) {
            return false;
        }
    }
}
