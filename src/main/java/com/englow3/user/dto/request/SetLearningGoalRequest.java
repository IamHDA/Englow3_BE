package com.englow3.user.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.CertificateType;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

/**
 * Certificate learners need a certificate and target score; general learners need a higher target level unless already
 * at C2. The service checks these conditional requirements for every caller.
 */
public record SetLearningGoalRequest(CertificateType certificateType, @DecimalMin("0") BigDecimal currentScore,
        @Positive BigDecimal targetScore, @Future LocalDate targetDate, CertificateLevel targetLevel,
        @Min(10) @Max(240) Integer dailyStudyMinutes) {
}
