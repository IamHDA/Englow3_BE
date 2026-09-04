package com.englow3.ai.foundation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AiBusinessStateReconciler {

    private final JdbcTemplate jdbcTemplate;

    AiBusinessStateReconciler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.reconcile-delay:1m}")
    @Transactional
    void reconcileTerminalFailures() {
        jdbcTemplate.update("""
                update ai_tutor_messages m set status = 'FAILED'
                from ai_jobs j
                where m.ai_job_id = j.id and m.status = 'PENDING'
                  and j.status in ('FAILED', 'CANCELLED')
                """);
        jdbcTemplate.update("""
                update speaking_sessions s set status = 'FAILED'
                from ai_jobs j
                where s.ai_job_id = j.id and s.status = 'PROCESSING'
                  and j.status in ('FAILED', 'CANCELLED')
                """);
        jdbcTemplate.update("""
                update ai_content_drafts d set status = 'FAILED'
                from ai_jobs j
                where d.ai_job_id = j.id and d.status = 'GENERATING'
                  and j.status in ('FAILED', 'CANCELLED')
                """);
        jdbcTemplate.update("""
                update writing_submissions s set status = 'FAILED'
                from ai_jobs j
                where s.ai_job_id = j.id and s.status = 'PROCESSING'
                  and j.status in ('FAILED', 'CANCELLED')
                """);
    }
}
