package com.englow3.speaking.entity;

/**
 * Same lifecycle as every other content type, and a separate enum for the same reason: separate module, separate
 * concern.
 */
public enum SpeakingPromptStatus {
    DRAFT, PENDING_REVIEW, REJECTED, PUBLISHED, ARCHIVED
}
