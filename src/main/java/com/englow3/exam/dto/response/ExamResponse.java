package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record ExamResponse(UUID id, String title, String description, ExamType examType,
        @Schema(nullable = true) CertificateType certificateType,
        @Schema(nullable = true) CertificateVariant certificateVariant,
        @Schema(nullable = true) TargetLevel targetLevel, int durationSeconds, BigDecimal maxRawScore,
        @Schema(nullable = true) BigDecimal passScore, ExamStatus status, int versionNumber, UUID createdByUserId,
        @Schema(nullable = true) Instant publishedAt, @Schema(nullable = true) Instant submittedForReviewAt,
        @Schema(nullable = true) UUID reviewedByUserId, @Schema(nullable = true) Instant reviewedAt,
        @Schema(nullable = true) String reviewNote) {

    public static ExamResponse from(ExamResult result) {
        return new ExamResponse(result.id(), result.title(), result.description(), result.examType(),
                result.certificateType(), result.certificateVariant(), result.targetLevel(), result.durationSeconds(),
                result.maxRawScore(), result.passScore(), result.status(), result.versionNumber(),
                result.createdByUserId(), result.publishedAt(), result.submittedForReviewAt(),
                result.reviewedByUserId(), result.reviewedAt(), result.reviewNote());
    }
}
