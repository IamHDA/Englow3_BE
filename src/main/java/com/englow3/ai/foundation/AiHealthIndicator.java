package com.englow3.ai.foundation;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.englow3.ai.speaking.SpeechProperties;
import com.englow3.shared.storage.ObjectStorageClient;

@Component("ai")
class AiHealthIndicator implements HealthIndicator {

    private final AiProperties aiProperties;
    private final SpeechProperties speechProperties;
    private final JdbcTemplate jdbcTemplate;
    private final RestClient aiServiceRestClient;
    private final ObjectStorageClient storage;

    AiHealthIndicator(AiProperties aiProperties, SpeechProperties speechProperties, JdbcTemplate jdbcTemplate,
            RestClient aiServiceRestClient, ObjectStorageClient storage) {
        this.aiProperties = aiProperties;
        this.speechProperties = speechProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.aiServiceRestClient = aiServiceRestClient;
        this.storage = storage;
    }

    @Override
    public Health health() {
        if ((aiProperties.enabled() || speechProperties.enabled()) && aiProperties.internalApiKey().isBlank()) {
            return Health.down().withDetail("reason", "AI service internal API key is missing").build();
        }
        if (!aiProperties.enabled() && !speechProperties.enabled()) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        try {
            aiServiceRestClient.get().uri("/health/ready").retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("reason", "AI service is not ready").build();
        }
        if (speechProperties.enabled() && !storage.isReachable()) {
            return Health.down().withDetail("reason", "Object storage is not reachable").build();
        }
        Long queued = jdbcTemplate.queryForObject("""
                select count(*) from ai_jobs where status in ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')
                """, Long.class);
        Long recentFailures = jdbcTemplate.queryForObject("""
                select count(*) from ai_jobs where status = 'FAILED' and completed_at > now() - interval '15 minutes'
                """, Long.class);
        Long oldestQueueSeconds = jdbcTemplate.queryForObject("""
                select coalesce(extract(epoch from now() - min(created_at))::bigint, 0)
                from ai_jobs where status in ('PENDING', 'RETRY_SCHEDULED')
                """, Long.class);
        Long unresolvedFeedback = jdbcTemplate.queryForObject("""
                select (select count(*) from ai_feedback_reports where status in ('OPEN', 'INVESTIGATING'))
                     + (select count(*) from ai_tutor_feedback where status = 'OPEN')
                """, Long.class);
        Health.Builder health = recentFailures != null && recentFailures >= 20 ? Health.status("DEGRADED")
                : Health.up();
        return health.withDetail("provider", aiProperties.provider()).withDetail("queuedJobs", queued)
                .withDetail("oldestQueueSeconds", oldestQueueSeconds).withDetail("recentFailures", recentFailures)
                .withDetail("unresolvedFeedback", unresolvedFeedback)
                .withDetail("speechEnabled", speechProperties.enabled()).build();
    }
}
