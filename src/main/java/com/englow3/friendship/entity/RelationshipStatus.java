package com.englow3.friendship.entity;

/**
 * How the caller stands with another learner. {@code UNAVAILABLE} means the other learner has blocked the caller; it
 * never says why, so a block is not revealed to the person it was placed on.
 */
public enum RelationshipStatus {
    NONE, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED, BLOCKED_BY_ME, UNAVAILABLE
}
