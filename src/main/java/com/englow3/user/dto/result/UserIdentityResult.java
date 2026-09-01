package com.englow3.user.dto.result;

import java.util.UUID;

import com.englow3.user.entity.Role;

/**
 * Who a caller is, in this application's own terms rather than the auth provider's. {@code isAdmin()} is what keeps
 * {@link Role} inside this module: a consumer asks the question instead of importing the enum to answer it itself.
 */
public record UserIdentityResult(UUID userId, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
