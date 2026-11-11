package com.englow3.user.dto.result;

import java.util.UUID;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;

public record AdminUserResult(UUID id, String email, String displayName, Role role) {
    public static AdminUserResult of(User u) {
        return new AdminUserResult(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole());
    }
}
