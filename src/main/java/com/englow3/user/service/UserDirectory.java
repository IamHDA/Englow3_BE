package com.englow3.user.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

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

    /** Far above the learners one instance serves; only there so a flood of distinct tokens cannot grow it forever. */
    private static final int MAX_CACHED_IDS = 10_000;

    private final UserRepository userRepo;
    private final CurrentUser currentUser;

    /**
     * Auth provider id to internal id. The pair is written once by the Supabase sync trigger and nothing updates or
     * deletes a user row, so it cannot go stale. Almost every endpoint starts here, and against a database a region
     * away the lookup was a round trip of its own - 100-200 ms on each request for an answer that never changes.
     */
    private final Map<UUID, UUID> userIds = new ConcurrentHashMap<>();

    /**
     * A valid token whose subject matches no row means the Supabase sync trigger did not fire - a real defect, and one
     * worth surfacing rather than papering over, so it is a 404 with a code that says what is missing. That answer is
     * not cached: the row may yet arrive.
     */
    public UUID requireCurrentUserId() {
        UUID authProviderId = currentUser.authProviderId();
        UUID cached = userIds.get(authProviderId);
        if (cached != null) {
            return cached;
        }
        UUID userId = userRepo.findByAuthProviderId(authProviderId).map(user -> user.getId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user is linked to auth provider id %s".formatted(authProviderId)));
        if (userIds.size() >= MAX_CACHED_IDS) {
            userIds.clear();
        }
        userIds.put(authProviderId, userId);
        return userId;
    }
}
