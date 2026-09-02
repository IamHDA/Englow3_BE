package com.englow3.exam.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

public record ExamListItemResponse(UUID id, String title, ExamType examType, CertificateType certificateType,
        CertificateVariant certificateVariant, TargetLevel targetLevel, ExamStatus status, int versionNumber,
        long sectionCount, long questionCount, UUID createdByUserId, Instant publishedAt, Instant createdAt) {

    public static ExamListItemResponse from(ExamListItemResult result) {
        return new ExamListItemResponse(result.id(), result.title(), result.examType(), result.certificateType(),
                result.certificateVariant(), result.targetLevel(), result.status(), result.versionNumber(),
                result.sectionCount(), result.questionCount(), result.createdByUserId(), result.publishedAt(),
                result.createdAt());
    }
}
