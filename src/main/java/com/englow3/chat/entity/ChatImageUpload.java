package com.englow3.chat.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * An image a participant was given an upload URL for and has not yet attached to a message. Sending the message
 * consumes the row; the cleanup job removes the rows, and the objects, that nobody sent within a day. A plain row.
 */
@Entity
@Table(name = "chat_image_uploads")
@Getter
public class ChatImageUpload extends BasePersistedEntity {

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Column(name = "object_key", nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "content_length", nullable = false, updatable = false)
    private long contentLength;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatImageUpload() {
    }

    public static ChatImageUpload issue(UUID id, UUID conversationId, UUID uploaderId, String objectKey,
            String contentType, long contentLength, Instant now) {
        ChatImageUpload upload = new ChatImageUpload();
        upload.id = id;
        upload.conversationId = conversationId;
        upload.uploaderId = uploaderId;
        upload.objectKey = objectKey;
        upload.contentType = contentType;
        upload.contentLength = contentLength;
        upload.createdAt = now;
        return upload;
    }
}
