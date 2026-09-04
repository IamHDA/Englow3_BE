package com.englow3.exam.entity;

/**
 * Not "2 skills" / "4 skills": TOEIC S&W is also two skills, so the count stops distinguishing anything the moment it
 * exists. The skill coverage of a paper is read off its sections instead.
 */
public enum CertificateVariant {
    LR, SW, ACADEMIC, GENERAL
}
