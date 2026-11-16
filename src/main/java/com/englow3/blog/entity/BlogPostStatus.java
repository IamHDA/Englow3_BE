package com.englow3.blog.entity;

/**
 * Where a post stands. Posting makes it PUBLISHED; a moderator hides it (HIDDEN, reversible) or removes it (REMOVED,
 * permanent).
 */
public enum BlogPostStatus {
    PUBLISHED, HIDDEN, REMOVED
}
