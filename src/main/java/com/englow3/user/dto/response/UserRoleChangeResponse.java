package com.englow3.user.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.englow3.user.entity.Role;
import com.englow3.user.dto.result.UserRoleChangeResult;

public record UserRoleChangeResponse(UUID id, UUID actorId, Role previousRole, Role newRole, String reason,
        Instant createdAt) {
    public static UserRoleChangeResponse from(UserRoleChangeResult e) {
        return new UserRoleChangeResponse(e.id(), e.actorId(), e.previousRole(), e.newRole(), e.reason(),
                e.createdAt());
    }
}
