package com.englow3.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The admin gate every module's {@code /api/admin/**} use case shares. It lives here rather than in {@code shared}
 * because {@code shared} may not know a {@code Role} - and because {@code user} already depends on {@code shared},
 * which would make the reverse edge a cycle. What it answers is "is this caller an admin, and who are they". Which
 * actions need an admin at all stays the calling module's decision, declared by that module choosing to call this.
 */
@Service
@RequiredArgsConstructor
public class AdminAccess {

    private final UserRepository userRepo;
    private final CurrentUser currentUser;

    /**
     * The gate and the internal user id are one lookup, because the token carries neither - it holds the auth provider
     * id only. That is also why there is no {@code @PreAuthorize} behind this: nothing puts a role authority on the
     * Authentication for it to check. ponytail: one uncached read per admin request. Admin traffic is negligible; cache
     * it, or put the role back in the token, only when that stops being true.
     */
    @Transactional(readOnly = true)
    public UUID requireAdminId() {
        UUID authProviderId = currentUser.authProviderId();
        User user = userRepo.findByAuthProviderId(authProviderId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user is linked to auth provider id %s".formatted(authProviderId)));

        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("ADMIN_ONLY", "This action is restricted to administrators");
        }
        return user.getId();
    }
}
