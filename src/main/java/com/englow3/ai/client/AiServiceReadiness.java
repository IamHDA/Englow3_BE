package com.englow3.ai.client;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A sleeping adapter is deferred without spending the provider retry budget. */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class AiServiceReadiness {
    private final RestClient client;
    private final LongSupplier nanoTime;
    private final ObjectMapper mapper = new ObjectMapper();
    private boolean checked;
    private boolean live;
    private long expiresAt;

    @Autowired
    public AiServiceReadiness(@Value("${app.ai.base-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        nanoTime = System::nanoTime;
    }

    AiServiceReadiness(RestClient client, LongSupplier nanoTime) {
        this.client = client;
        this.nanoTime = nanoTime;
    }

    /** One bounded wake-up, outside readiness and without inference or a keep-alive loop. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread.ofVirtual().name("ai-readiness-warmup").start(this::isLive);
    }

    /** Share a short-lived liveness observation between the two worker lanes. */
    public synchronized boolean isLive() {
        if (checked && nanoTime.getAsLong() - expiresAt < 0)
            return live;
        try {
            String result = client.get().uri("/health/live").retrieve().body(String.class);
            live = "up".equals(mapper.readTree(result).path("status").asText());
        } catch (Exception unavailable) {
            live = false;
        }
        checked = true;
        expiresAt = nanoTime.getAsLong() + Duration.ofSeconds(live ? 5 : 1).toNanos();
        return live;
    }
}
