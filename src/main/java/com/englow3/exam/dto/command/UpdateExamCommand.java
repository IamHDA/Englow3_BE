package com.englow3.exam.dto.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

public record UpdateExamCommand(UUID examId, String title, String description, ExamType examType,
        CertificateType certificateType, CertificateVariant certificateVariant, TargetLevel targetLevel,
        int durationSeconds, BigDecimal maxRawScore, BigDecimal passScore) {
}
