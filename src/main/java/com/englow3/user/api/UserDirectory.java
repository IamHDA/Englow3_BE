package com.englow3.user.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserDirectory {
    default boolean isBackOfficeUser(UUID id) {
        return false;
    }

    default java.util.List<StaffMember> backOfficeUsers() {
        return java.util.List.of();
    }

    record StaffMember(UUID id, String displayName) {
    }

    UUID requireCurrentUserId();

    /**
     * Drops what is remembered about a sign-in whose account was just erased. The id lookup is cached on the
     * assumption that a user row never goes away; erasing an account is the one thing that breaks it, and a token that
     * has not yet expired would otherwise keep resolving to a user that no longer exists.
     */
    default void forget(UUID authProviderId) {
    }

    default Map<UUID, String> displayNames(Collection<UUID> ids) {
        return Map.of();
    }
}
