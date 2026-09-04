package com.englow3.exam.entity;

/**
 * Deliberately not {@code com.englow3.user.entity.CertificateType}: there the value is a learner's goal, here it is one
 * half of what identifies a paper, and the other half is {@link CertificateVariant}. Two enums, translated at the call
 * boundary - never one enum shared between the modules.
 */
public enum CertificateType {
    IELTS, TOEIC
}
