package com.englow3.ai.foundation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

@RestController
@RequestMapping("/api/ai/notifications")
class AiNotificationController {

    private final JdbcTemplate jdbcTemplate;
    private final UserDirectory userDirectory;

    AiNotificationController(JdbcTemplate jdbcTemplate, UserDirectory userDirectory) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<NotificationResponse> list() {
        UUID userId = userId();
        return jdbcTemplate.query("""
                select id, notification_type, target_type, target_id, read_at, created_at
                from ai_notifications where user_id = ? order by created_at desc limit 100
                """,
                (rs, row) -> new NotificationResponse(rs.getObject("id", UUID.class), rs.getString("notification_type"),
                        rs.getString("target_type"), rs.getObject("target_id", UUID.class),
                        instant(rs.getTimestamp("read_at")), rs.getTimestamp("created_at").toInstant()),
                userId);
    }

    @PutMapping("/{notificationId}/read")
    @Transactional
    ResponseEntity<Void> markRead(@PathVariable UUID notificationId) {
        int updated = jdbcTemplate.update("""
                update ai_notifications set read_at = coalesce(read_at, now()) where id = ? and user_id = ?
                """, notificationId, userId());
        if (updated == 0) {
            throw new NotFoundException("AI_NOTIFICATION_NOT_FOUND", "AI notification was not found");
        }
        return ResponseEntity.noContent().build();
    }

    private UUID userId() {
        return userDirectory.requireCurrentUserId();
    }

    private Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    record NotificationResponse(UUID id, String type, String targetType, UUID targetId, Instant readAt,
            Instant createdAt) {
    }
}
