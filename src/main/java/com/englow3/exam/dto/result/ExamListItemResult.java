package com.englow3.exam.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

/**
 * No description - it is a text column and the list has no room for it. {@code createdByUserId} is not resolved to a
 * name either: the frontend composes it against the admin user list, which costs less than the first exam -> user edge.
 * No attempt count yet - that one waits for the sitting, which has no entity.
 */
public record ExamListItemResult(UUID id, String title, ExamType examType, CertificateType certificateType,
        CertificateVariant certificateVariant, TargetLevel targetLevel, ExamStatus status, int versionNumber,
        long sectionCount, long questionCount, UUID createdByUserId, Instant publishedAt, Instant createdAt) {

    public static ExamListItemResult of(Exam exam, long sectionCount, long questionCount) {
        return new ExamListItemResult(exam.getId(), exam.getTitle(), exam.getExamType(), exam.getCertificateType(),
                exam.getCertificateVariant(), exam.getTargetLevel(), exam.getStatus(), exam.getVersionNumber(),
                sectionCount, questionCount, exam.getCreatedByUserId(), exam.getPublishedAt(), exam.getCreatedAt());
    }
}
