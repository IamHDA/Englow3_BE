package com.englow3.user.dto.response;

import java.util.UUID;
import com.englow3.user.entity.Role;
import com.englow3.user.dto.result.AdminUserResult;

public record AdminUserResponse(UUID id, String email, String displayName, Role role) {
    public static AdminUserResponse from(AdminUserResult u) {
        return new AdminUserResponse(u.id(), u.email(), u.displayName(), u.role());
    }
}
