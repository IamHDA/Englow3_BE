package com.englow3.user.dto.result;

import java.time.Instant;
import java.util.UUID;
import com.englow3.user.entity.Role;

public record UserRoleChangeResult(UUID id, UUID actorId, Role previousRole, Role newRole, String reason,
        Instant createdAt) {
}
