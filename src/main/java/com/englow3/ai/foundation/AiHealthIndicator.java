package com.englow3.ai.foundation;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.englow3.ai.speaking.SpeechProperties;

@Component("ai")
class AiHealthIndicator implements HealthIndicator {

    private final AiProperties aiProperties;
    private final SpeechProperties speechProperties;
    private final JdbcTemplate jdbcTemplate;

    AiHealthIndicator(AiProperties aiProperties, SpeechProperties speechProperties, JdbcTemplate jdbcTemplate) {
        this.aiProperties = aiProperties;
        this.speechProperties = speechProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        if ((aiProperties.enabled() || speechProperties.enabled()) && aiProperties.internalApiKey().isBlank()) {
            return Health.down().withDetail("reason", "AI service internal API key is missing").build();
        }
        if (!aiProperties.enabled() && !speechProperties.enabled()) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        Long queued = jdbcTemplate.queryForObject("""
                select count(*) from ai_jobs where status in ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')
                """, Long.class);
        Long recentFailures = jdbcTemplate.queryForObject("""
                select count(*) from ai_jobs where status = 'FAILED' and completed_at > now() - interval '15 minutes'
                """, Long.class);
        Health.Builder health = recentFailures != null && recentFailures >= 20 ? Health.status("DEGRADED")
                : Health.up();
        return health.withDetail("provider", aiProperties.provider()).withDetail("queuedJobs", queued)
                .withDetail("recentFailures", recentFailures).withDetail("speechEnabled", speechProperties.enabled())
                .build();
    }
}
