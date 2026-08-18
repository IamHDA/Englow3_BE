package com.englow3.user.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.englow3.user.entity.CertificateType;

public record SetLearningGoalCommand(CertificateType certificateType, BigDecimal targetScore, LocalDate targetDate) {
}
