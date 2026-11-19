package com.englow3.chat.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One message: text, one image, or both - never neither. Only the sender recalls it, once, and a recalled message keeps
 * nothing of what it said; there is no editing. {@code senderId} is null once the sender's account is erased, and the
 * message stays for the other person.
 * <p>
 * The same two rules - never empty, text at most 4000 characters - are also check constraints, because a row must not
 * be able to break them by any route. Here they give the caller an error with a code instead of a constraint name.
 */
@Entity
@Table(name = "chat_messages")
@Getter
public class ChatMessage extends BasePersistedEntity {

    /**
     * Counted in {@code String.length()}, the same unit as {@code @Size} on the request. Postgres counts characters, so
     * for text outside the Basic Multilingual Plane (emoji, say) this is the stricter of the two: it never lets through
     * what {@code ck_chat_messages_body_length} would refuse.
     */
    public static final int MAX_BODY_LENGTH = 4000;

    /** How much of the text the conversation list shows, counted in characters as {@code left(body, n)} does. */
    public static final int PREVIEW_LENGTH = 120;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "image_object_key")
    private String imageObjectKey;

    @Column(name = "recalled_at")
    private Instant recalledAt;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected ChatMessage() {
    }

    /**
     * Text that is only whitespace counts as no text; text that has words is kept exactly as typed, spaces and line
     * breaks included.
     */
    public static ChatMessage send(UUID conversationId, long seq, UUID senderId, String clientId, String body,
            String imageObjectKey, Instant now) {
        String text = body == null || body.isBlank() ? null : body;
        if (text == null && imageObjectKey == null) {
            throw new BadRequestException("CHAT_MESSAGE_EMPTY", "A message needs text or an image");
        }
        if (text != null && text.length() > MAX_BODY_LENGTH) {
            throw new BadRequestException("CHAT_MESSAGE_TOO_LONG",
                    "A message may be at most %d characters".formatted(MAX_BODY_LENGTH));
        }
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID();
        message.conversationId = conversationId;
        message.seq = seq;
        message.senderId = senderId;
        message.clientId = clientId;
        message.body = text;
        message.imageObjectKey = imageObjectKey;
        message.sentAt = now;
        return message;
    }

    /**
     * Takes back the text and the image. Returns the image's object key, which the caller must delete from storage
     * after the transaction commits, or null when there was no image.
     */
    public String recall(UUID actorId, Instant now) {
        if (senderId == null || !senderId.equals(actorId)) {
            throw new ForbiddenException("CHAT_MESSAGE_NOT_SENDER", "Only the sender can recall a message");
        }
        if (recalledAt != null) {
            throw new ConflictException("CHAT_MESSAGE_ALREADY_RECALLED", "This message has already been recalled");
        }
        String removedImageKey = imageObjectKey;
        body = null;
        imageObjectKey = null;
        recalledAt = now;
        return removedImageKey;
    }

    public boolean isRecalled() {
        return recalledAt != null;
    }

    /** The start of the text for the conversation list, or null when there is none (an image only, or recalled). */
    public String preview() {
        if (body == null || body.codePointCount(0, body.length()) <= PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, body.offsetByCodePoints(0, PREVIEW_LENGTH));
    }
}
