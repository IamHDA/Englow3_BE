package com.englow3.ai.foundation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AiOperationsCleanupWorker {

    private final JdbcTemplate jdbcTemplate;

    AiOperationsCleanupWorker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${app.ai.events.cleanup-cron:0 30 3 * * *}")
    @Transactional
    void removeExpiredOperationalData() {
        jdbcTemplate.update("delete from ai_notifications where created_at < now() - interval '90 days'");
        jdbcTemplate.update("delete from ai_job_events where created_at < now() - interval '90 days'");
    }
}
