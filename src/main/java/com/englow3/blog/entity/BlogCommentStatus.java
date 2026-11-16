package com.englow3.blog.entity;

/**
 * Where a comment stands. Writing it makes it VISIBLE; a moderator hides it (HIDDEN, reversible) or removes it
 * (REMOVED, permanent).
 */
public enum BlogCommentStatus {
    VISIBLE, HIDDEN, REMOVED
}
