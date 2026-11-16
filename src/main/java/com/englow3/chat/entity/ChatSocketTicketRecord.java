package com.englow3.chat.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A ticket that lets a browser open the chat socket without holding its access token: valid for a few seconds, used
 * once. Only the hash of the ticket is kept. A plain row - the one rule, single use, is a conditional {@code UPDATE} in
 * the repository because it has to hold across instances. Named for the table so it does not collide with the result
 * record the service hands out.
 */
@Entity
@Table(name = "chat_socket_tickets")
@Getter
public class ChatSocketTicketRecord {

    @Id
    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatSocketTicketRecord() {
    }

    public static ChatSocketTicketRecord issue(String tokenHash, UUID userId, Instant now, Duration ttl) {
        ChatSocketTicketRecord record = new ChatSocketTicketRecord();
        record.tokenHash = tokenHash;
        record.userId = userId;
        record.expiresAt = now.plus(ttl);
        record.createdAt = now;
        return record;
    }
}
