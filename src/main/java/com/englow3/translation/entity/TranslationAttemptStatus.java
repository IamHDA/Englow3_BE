package com.englow3.translation.entity;

/**
 * Where one translation stands. There is no RUNNING: whether a worker has the job in hand is the queue's business, and
 * QUEUED - "we have it and there is no grade yet" - is all a learner waiting needs to know.
 */
public enum TranslationAttemptStatus {

    /** Submitted and grading has been asked for. */
    QUEUED,

    GRADED,

    /** Grading will not happen. {@code errorCode} says why. */
    FAILED
}
