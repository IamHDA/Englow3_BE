package com.englow3.user.api;

import java.util.UUID;

public interface UserDirectory {
    UUID requireCurrentUserId();

    default java.util.Map<UUID, String> displayNames(java.util.Collection<UUID> ids) {
        return java.util.Map.of();
    }
}
