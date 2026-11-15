package com.englow3.friendship.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One learner's block on another. A plain row with no rule of its own: what a block does to the pair's friendship spans
 * both tables and lives in the service. Rows are written by a native insert, so this class exists to read the list.
 */
@Entity
@Table(name = "user_blocks")
@Getter
public class UserBlock {

    @EmbeddedId
    private UserBlockId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserBlock() {
    }

    public static UserBlock of(UUID blockerId, UUID blockedId, Instant now) {
        UserBlock block = new UserBlock();
        block.id = new UserBlockId(blockerId, blockedId);
        block.createdAt = now;
        return block;
    }
}
