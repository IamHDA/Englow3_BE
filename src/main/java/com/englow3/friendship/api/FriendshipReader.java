package com.englow3.friendship.api;

import java.util.Set;
import java.util.UUID;

/**
 * What the modules that show people (chat, blog) may ask about learners' relationships. It exposes answers, never rows:
 * a block is not something the person it was placed on can be told about.
 */
public interface FriendshipReader {

    /** An accepted friendship between current learners. Role changes suspend it; a block deletes it. */
    boolean areFriends(UUID a, UUID b);

    boolean isBlockedEitherWay(UUID a, UUID b);

    /** Everyone {@code userId} has blocked plus everyone who has blocked {@code userId}. For filtering lists. */
    Set<UUID> blockedEitherWay(UUID userId);
}
