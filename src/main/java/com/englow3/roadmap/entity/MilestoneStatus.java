package com.englow3.roadmap.entity;

public enum MilestoneStatus {
    PENDING, PASSED,
    /** The last attempt fell short. The milestone can be taken again; a pass replaces this. */
    FAILED
}
