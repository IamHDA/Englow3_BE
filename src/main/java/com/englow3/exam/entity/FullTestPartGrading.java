package com.englow3.exam.entity;

/**
 * Where the grading of a full test's Writing or Speaking answer stands, as the sitting screen shows it. Read from the
 * assessment module on each view; never stored.
 */
public enum FullTestPartGrading {
    /** Queued for the automatic grader. */
    GRADING,
    /** Waiting for a person to grade it. */
    AWAITING_REVIEW,
    /** The automatic grader gave up; the learner can resend it or ask for a person. */
    FAILED, GRADED;

    /** From the assessment module's attempt status; null for one with no answer yet (a draft). */
    public static FullTestPartGrading of(String attemptStatus) {
        if (attemptStatus == null) {
            return null;
        }
        return switch (attemptStatus) {
            case "QUEUED" -> GRADING;
            case "NEEDS_REVIEW" -> AWAITING_REVIEW;
            case "FAILED" -> FAILED;
            case "COMPLETED" -> GRADED;
            default -> null;
        };
    }
}
