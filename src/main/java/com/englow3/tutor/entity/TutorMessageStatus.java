package com.englow3.tutor.entity;

/**
 * Whether a turn has something to show yet.
 * <p>
 * A learner's own message is {@link #READY} the moment it arrives - there is nothing to wait for. Only the tutor's side
 * is ever {@link #PENDING}, and the distinction from {@link #FAILED} is what lets the screen say "still thinking"
 * rather than showing an empty bubble that never fills.
 */
public enum TutorMessageStatus {

    PENDING,

    READY,

    FAILED
}
