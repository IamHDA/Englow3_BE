package com.englow3.ai.foundation;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.englow3.ai.foundation.AiJobEventPublisher.AiJobEvent;

@Component
class AiJobEventStream {

    private static final int MAX_REPLAY_EVENTS = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    AiJobEventStream(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    SseEmitter subscribe(UUID userId, long afterEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> userSubscribers = subscribers.computeIfAbsent(userId,
                ignored -> new CopyOnWriteArrayList<>());
        userSubscribers.add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        List<AiJobEvent> replay = jdbcTemplate.query("""
                select event_id, requester_user_id, event_type, payload::text, created_at
                from ai_job_events
                where requester_user_id = ? and event_id > ?
                order by event_id limit ?
                """,
                (rs, row) -> new AiJobEvent(rs.getLong("event_id"), rs.getObject("requester_user_id", UUID.class),
                        rs.getString("event_type"), rs.getString("payload"), rs.getTimestamp("created_at").toInstant()),
                userId, afterEventId, MAX_REPLAY_EVENTS + 1);
        if (replay.size() > MAX_REPLAY_EVENTS) {
            send(emitter, userId, new AiJobEvent(afterEventId, userId, "RESYNC_REQUIRED",
                    "{\"status\":\"RESYNC_REQUIRED\"}", java.time.Instant.now()));
        } else {
            replay.forEach(event -> send(emitter, userId, event));
        }
        return emitter;
    }

    void publish(AiJobEvent event) {
        if (event.userId() == null) {
            return;
        }
        subscribers.getOrDefault(event.userId(), new CopyOnWriteArrayList<>())
                .forEach(emitter -> send(emitter, event.userId(), event));
    }

    @Scheduled(fixedDelayString = "${app.ai.events.heartbeat-delay:15s}")
    void heartbeat() {
        subscribers.forEach((userId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").comment("keep-alive"));
            } catch (IOException | IllegalStateException exception) {
                remove(userId, emitter);
            }
        }));
    }

    private void send(SseEmitter emitter, UUID userId, AiJobEvent event) {
        try {
            emitter.send(SseEmitter.event().id(Long.toString(event.id())).name(event.eventType()).data(event.payload())
                    .reconnectTime(2_000));
            if (event.id() > 0) {
                jdbcTemplate.update("""
                        update ai_job_events set delivery_count = delivery_count + 1, last_delivered_at = now()
                        where event_id = ? and requester_user_id = ?
                        """, event.id(), userId);
            }
        } catch (IOException | IllegalStateException exception) {
            remove(userId, emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(userId, emitters);
            }
        }
    }
}
