package com.englow3.friendship.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class UserBlockId implements Serializable {

    @Column(name = "blocker_id", nullable = false, updatable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false, updatable = false)
    private UUID blockedId;

    protected UserBlockId() {
    }

    public UserBlockId(UUID blockerId, UUID blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserBlockId that && Objects.equals(blockerId, that.blockerId)
                && Objects.equals(blockedId, that.blockedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockerId, blockedId);
    }
}
