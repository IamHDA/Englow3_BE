package com.englow3.roadmap.entity;

public enum LevelCheckStatus {
    /** The written part (vocabulary/grammar and listening) is not scored yet. */
    IN_PROGRESS,
    /** The written part is scored; the spoken answer is not graded yet, or not given. */
    AWAITING_SPEAKING, COMPLETED
}
