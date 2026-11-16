package com.englow3.blog.entity;

/** A report is OPEN until a moderator closes it, once: RESOLVED when they acted on the content, DISMISSED when not. */
public enum BlogReportStatus {
    OPEN, RESOLVED, DISMISSED
}
