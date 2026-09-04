package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

public record ExamResult(UUID id, String title, String description, ExamType examType, CertificateType certificateType,
        CertificateVariant certificateVariant, TargetLevel targetLevel, int durationSeconds, BigDecimal maxRawScore,
        BigDecimal passScore, ExamStatus status, int versionNumber, UUID createdByUserId, Instant publishedAt) {

    public static ExamResult of(Exam exam) {
        return new ExamResult(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getExamType(),
                exam.getCertificateType(), exam.getCertificateVariant(), exam.getTargetLevel(),
                exam.getDurationSeconds(), exam.getMaxRawScore(), exam.getPassScore(), exam.getStatus(),
                exam.getVersionNumber(), exam.getCreatedByUserId(), exam.getPublishedAt());
    }
}
