package com.englow3.ai.foundation;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
class AiJobEventPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiJobEventStream eventStream;

    AiJobEventPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AiJobEventStream eventStream) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventStream = eventStream;
    }

    void record(AiJob job, String eventType) {
        ObjectNode payload = safePayload(objectMapper, job, eventType);
        AiJobEvent event = jdbcTemplate.query("""
                insert into ai_job_events
                    (job_id, requester_user_id, capability, event_type, retry_count, payload)
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (job_id, event_type, retry_count) do nothing
                returning event_id, created_at
                """,
                rs -> rs.next()
                        ? new AiJobEvent(rs.getLong("event_id"), job.getRequesterUserId(), eventType,
                                payload.toString(), rs.getTimestamp("created_at").toInstant())
                        : null,
                job.getId(), job.getRequesterUserId(), job.getCapability().name(), eventType, job.getRetryCount(),
                payload.toString());
        if (event == null) {
            return;
        }
        if (job.getRequesterUserId() != null && (eventType.equals("SUCCEEDED") || eventType.equals("FAILED"))) {
            jdbcTemplate.update("""
                    insert into ai_notifications
                        (id, user_id, job_id, event_id, notification_type, target_type, target_id)
                    values (?, ?, ?, ?, ?, ?, ?) on conflict do nothing
                    """, UUID.randomUUID(), job.getRequesterUserId(), job.getId(), event.id(),
                    eventType.equals("SUCCEEDED") ? "AI_JOB_SUCCEEDED" : "AI_JOB_FAILED", job.getTargetType(),
                    job.getTargetId());
        }
        afterCommit(() -> eventStream.publish(event));
    }

    static ObjectNode safePayload(ObjectMapper objectMapper, AiJob job, String eventType) {
        ObjectNode payload = objectMapper.createObjectNode().put("jobId", job.getId().toString())
                .put("status", eventType).put("capability", job.getCapability().name())
                .put("targetType", job.getTargetType()).put("targetId", job.getTargetId().toString());
        if (job.getErrorCode() != null) {
            payload.put("errorCode", job.getErrorCode());
        }
        return payload;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    record AiJobEvent(long id, UUID userId, String eventType, String payload, java.time.Instant createdAt) {
    }
}
