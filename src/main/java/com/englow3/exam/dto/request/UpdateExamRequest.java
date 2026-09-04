package com.englow3.exam.dto.request;

import java.math.BigDecimal;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A full replacement of the shell, not a patch - every field is sent, so an omitted one is an explicit null rather than
 * "leave it alone". Same validation as {@code CreateExamRequest}; whether the certificate type and variant agree is a
 * cross-field rule and lives in {@code Exam.updateDraft(...)}.
 */
public record UpdateExamRequest(@NotBlank @Size(max = 100) String title, @NotBlank String description,
        @NotNull ExamType examType, CertificateType certificateType, CertificateVariant certificateVariant,
        TargetLevel targetLevel, @Positive int durationSeconds, @NotNull @Positive BigDecimal maxRawScore,
        @PositiveOrZero BigDecimal passScore) {
}
