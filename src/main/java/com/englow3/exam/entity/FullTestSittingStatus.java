package com.englow3.exam.entity;

/** Where a learner's full test sitting is. See {@code V082__create_ielts_full_tests.sql}. */
public enum FullTestSittingStatus {
    IN_PROGRESS, AWAITING_GRADES, COMPLETED, EXPIRED
}
