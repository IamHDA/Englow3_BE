package com.englow3.user.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import com.englow3.shared.persistence.BasePersistedEntity;

@Entity
@Table(name = "user_role_changes")
@Getter
public class UserRoleChange extends BasePersistedEntity {
    private UUID userId;
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    private Role previousRole;
    @Enumerated(EnumType.STRING)
    private Role newRole;
    private String reason;
    private Instant createdAt;

    protected UserRoleChange() {
    }

    public static UserRoleChange of(UUID user, UUID actor, Role previous, Role next, String reason, Instant now) {
        var e = new UserRoleChange();
        e.id = UUID.randomUUID();
        e.userId = user;
        e.actorId = actor;
        e.previousRole = previous;
        e.newRole = next;
        e.reason = reason;
        e.createdAt = now;
        return e;
    }
}
