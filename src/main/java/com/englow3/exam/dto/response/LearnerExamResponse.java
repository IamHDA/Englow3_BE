package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.dto.result.LearnerExamListItemResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

public record LearnerExamResponse(UUID id, String title, String description, ExamType examType,
        CertificateType certificateType, CertificateVariant certificateVariant, TargetLevel targetLevel,
        int durationSeconds, BigDecimal maxRawScore, BigDecimal passScore, long questionCount, ExamStatus status,
        Instant publishedAt) {

    public static LearnerExamResponse from(LearnerExamListItemResult result) {
        return new LearnerExamResponse(result.id(), result.title(), result.description(), result.examType(),
                result.certificateType(), result.certificateVariant(), result.targetLevel(), result.durationSeconds(),
                result.maxRawScore(), result.passScore(), result.questionCount(), result.status(),
                result.publishedAt());
    }
}
