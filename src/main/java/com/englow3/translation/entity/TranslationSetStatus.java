package com.englow3.translation.entity;

/**
 * Same lifecycle as every other content type, and a separate enum for the same reason: separate module, separate
 * concern.
 */
public enum TranslationSetStatus {
    DRAFT, PENDING_REVIEW, REJECTED, PUBLISHED, ARCHIVED
}
