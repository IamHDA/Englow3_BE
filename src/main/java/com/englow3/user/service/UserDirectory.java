package com.englow3.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Identity only - "which internal user is calling". Nothing here decides what anyone is allowed to do: that moved
 * entirely to {@code @PreAuthorize("hasRole(...)")} reading the role from the token, so a gate costs no query at all
 * and only a use case that genuinely needs the id pays for one. It lives in {@code user} because that module owns the
 * {@code users} table. Another module resolving the id by injecting {@code UserRepository} itself would be reaching
 * into this module's persistence - the reason for this class is to keep that one call on the right side of the
 * boundary, not to add a layer.
 */
@Service
@RequiredArgsConstructor
public class UserDirectory {

    private final UserRepository userRepo;
    private final CurrentUser currentUser;

    /**
     * A valid token whose subject matches no row means the Supabase sync trigger did not fire - a real defect, and one
     * worth surfacing rather than papering over, so it is a 404 with a code that says what is missing.
     */
    @Transactional(readOnly = true)
    public UUID requireCurrentUserId() {
        UUID authProviderId = currentUser.authProviderId();
        return userRepo.findByAuthProviderId(authProviderId).map(user -> user.getId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user is linked to auth provider id %s".formatted(authProviderId)));
    }
}
