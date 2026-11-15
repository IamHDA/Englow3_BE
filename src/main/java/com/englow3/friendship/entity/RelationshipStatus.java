package com.englow3.friendship.entity;

/**
 * How a viewer stands with another person, for the "Add friend" button on a learner's wall and wherever else one is
 * shown. Only {@code NONE} offers "Add friend"; a request offers cancel or accept, a friend offers unfriend, a block
 * the viewer placed offers unblock, and the last three offer nothing at all.
 * <ul>
 * <li>{@code SELF}: the viewer is looking at their own page.</li>
 * <li>{@code NOT_APPLICABLE}: the viewer or the other person is not a learner (staff, or an id that names no account),
 * so no friend action exists between them.</li>
 * <li>{@code UNAVAILABLE}: the other learner has blocked the viewer. It never says why, so a block is not revealed to
 * the person it was placed on.</li>
 * </ul>
 */
public enum RelationshipStatus {
    NONE, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED, BLOCKED_BY_ME, UNAVAILABLE, SELF, NOT_APPLICABLE
}
