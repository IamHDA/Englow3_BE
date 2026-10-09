package com.englow3.user.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserDirectory {
    UUID requireCurrentUserId();

    default Map<UUID, String> displayNames(Collection<UUID> ids) {
        return Map.of();
    }
}
