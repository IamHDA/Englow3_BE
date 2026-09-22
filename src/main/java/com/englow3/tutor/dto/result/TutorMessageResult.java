package com.englow3.tutor.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.tutor.entity.TutorMessage;

/**
 * One turn as a screen sees it.
 *
 * @param content
 *            null while a reply is pending. Deliberately not an empty string: the screen has to tell "still thinking"
 *            from "answered with nothing", and one value for both would make that impossible.
 * @param errorCode
 *            why no answer came, when none did. Shown as an explanation rather than a bubble that never fills.
 */
public record TutorMessageResult(UUID id, int orderNo, String role, String status, String content, String errorCode,
        String model, boolean reported, Instant createdAt, Instant answeredAt) {

    public static TutorMessageResult of(TutorMessage message) {
        return new TutorMessageResult(message.getId(), message.getOrderNo(), message.getRole().name(),
                message.getStatus().name(), message.getContent(), message.getErrorCode(), message.getModel(),
                message.getReportedAt() != null, message.getCreatedAt(), message.getAnsweredAt());
    }
}
