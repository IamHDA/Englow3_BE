package com.englow3.exam.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record ExamListItemResponse(UUID id, String title, ExamType examType,
        @Schema(nullable = true) CertificateType certificateType,
        @Schema(nullable = true) CertificateVariant certificateVariant,
        @Schema(nullable = true) TargetLevel targetLevel, ExamStatus status, int versionNumber, UUID createdByUserId,
        @Schema(nullable = true) Instant publishedAt, Instant createdAt,
        @Schema(nullable = true) Instant submittedForReviewAt, @Schema(nullable = true) String reviewNote) {

    public static ExamListItemResponse from(ExamListItemResult result) {
        return new ExamListItemResponse(result.id(), result.title(), result.examType(), result.certificateType(),
                result.certificateVariant(), result.targetLevel(), result.status(), result.versionNumber(),
                result.createdByUserId(), result.publishedAt(), result.createdAt(), result.submittedForReviewAt(),
                result.reviewNote());
    }
}
