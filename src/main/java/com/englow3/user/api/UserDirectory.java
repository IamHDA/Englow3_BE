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

    default Map<UUID, String> displayNames(Collection<UUID> ids) {
        return Map.of();
    }
}
