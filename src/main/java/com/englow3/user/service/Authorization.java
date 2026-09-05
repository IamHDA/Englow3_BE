package com.englow3.user.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

/**
 * The one place that answers "what is this caller allowed to do". It lives in {@code user} because that module owns
 * {@code Role} and the {@code users} table - putting it in {@code shared} would make {@code shared} depend on
 * {@code user}, which already depends on {@code shared}, and a package cycle is not worth the shorter import. The bean
 * is named {@code authorization} on purpose: {@code @PreAuthorize("@authorization.isAdmin()")} resolves by bean name,
 * not by package, so controllers reference it without knowing where it lives. Two shapes over one lookup, not two
 * components: a boolean for {@code @PreAuthorize}, and {@link #requireAdminId()} for a use case that also needs the
 * caller's own id - {@code exams.created_by_user_id} is why that exists. The role is read per request rather than taken
 * from a JWT claim, so granting or revoking one takes effect immediately; the cost is one uncached read on admin
 * traffic, which is negligible. ponytail: read per request, uncached. Put {@code user_id} in the token first if a
 * lookup on *every* request ever hurts - that claim is immutable so a stale copy is never wrong, unlike a role.
 */
@Service("authorization")
public class Authorization {

    private static final Logger log = LoggerFactory.getLogger(Authorization.class);

    private final UserRepository userRepo;
    private final CurrentUser currentUser;

    Authorization(UserRepository userRepo, CurrentUser currentUser) {
        this.userRepo = userRepo;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public boolean isAdmin() {
        return hasAnyRole(Role.ADMIN);
    }

    /** An admin is staff too - the roles are a ladder, not a partition. */
    @Transactional(readOnly = true)
    public boolean isStaff() {
        return hasAnyRole(Role.STAFF, Role.ADMIN);
    }

    /**
     * The gate and the caller's internal id in one lookup. Refuses with the same {@code ACCESS_DENIED} the
     * {@code @PreAuthorize} path produces, so one refusal never reaches the client under two different codes.
     */
    @Transactional(readOnly = true)
    public UUID requireAdminId() {
        User user = currentUser().orElseThrow(Authorization::accessDenied);
        if (user.getRole() != Role.ADMIN) {
            throw accessDenied();
        }
        return user.getId();
    }

    private boolean hasAnyRole(Role... allowed) {
        return currentUser().map(user -> List.of(allowed).contains(user.getRole())).orElse(false);
    }

    /**
     * A token whose subject has no row means the Supabase sync trigger did not fire - a real defect, but not one the
     * caller can act on, so it is logged here and answered as a refusal rather than leaking out as a 404.
     */
    private Optional<User> currentUser() {
        UUID authProviderId = currentUser.authProviderId();
        Optional<User> user = userRepo.findByAuthProviderId(authProviderId);
        if (user.isEmpty()) {
            log.warn("Authenticated token carries auth provider id {} that matches no user row", authProviderId);
        }
        return user;
    }

    private static ForbiddenException accessDenied() {
        return new ForbiddenException("ACCESS_DENIED", "You are not allowed to perform this action");
    }
}
