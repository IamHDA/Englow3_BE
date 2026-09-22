package com.englow3.exam.entity;

/**
 * Where a paper is in its life.
 * <p>
 * The path is DRAFT to PENDING_REVIEW to PUBLISHED, with REJECTED as the way back. An administrator may still publish a
 * draft outright: they hold the approval power anyway, and making them submit a paper to themselves would be ceremony,
 * not control.
 */
public enum ExamStatus {

    /** Being written. Editable, and the only state besides REJECTED that is. */
    DRAFT,

    /** Submitted and waiting on an administrator. Frozen, so a reviewer reads what the author sent. */
    PENDING_REVIEW,

    /** Turned back with a note. Editable again, and can be resubmitted. */
    REJECTED,

    PUBLISHED,

    ARCHIVED
}
