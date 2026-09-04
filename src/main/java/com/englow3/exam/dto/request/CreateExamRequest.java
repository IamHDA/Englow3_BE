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
 * Whether the certificate type and variant agree is not expressible here - that is a cross-field rule and it lives in
 * {@code Exam.draft(...)}.
 */
public record CreateExamRequest(@NotBlank @Size(max = 100) String title, @NotBlank String description,
        @NotNull ExamType examType, CertificateType certificateType, CertificateVariant certificateVariant,
        TargetLevel targetLevel, @Positive int durationSeconds, @NotNull @Positive BigDecimal maxRawScore,
        @PositiveOrZero BigDecimal passScore) {
}
