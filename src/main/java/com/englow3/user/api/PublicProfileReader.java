package com.englow3.user.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * What other learners may see of an account: name and picture, never email or profile details. The user module owns
 * identity, so modules that show people (friendship, chat, blog) ask here instead of reading {@code users}.
 */
public interface PublicProfileReader {

    record PublicProfile(UUID id, String displayName, String avatarUrl) {
    }

    /** One query for the whole set. Ids with no user are absent from the map. Any role. */
    Map<UUID, PublicProfile> profiles(Collection<UUID> ids);

    /** True only for an existing account whose current role is LEARNER. */
    boolean isLearner(UUID id);

    /**
     * Current learner ids, protected against role changes until the caller's write transaction ends. Locks accounts in
     * database id order; call before taking any feature-specific locks. Never use this on a read-only path.
     */
    Set<UUID> lockLearners(Collection<UUID> ids);

    /**
     * Learners whose display name contains {@code term} (case-insensitive), ordered by display name then id.
     * {@code excludedIds} is never empty in practice: callers always exclude themselves.
     */
    Page<PublicProfile> searchLearners(String term, Collection<UUID> excludedIds, Pageable page);
}
