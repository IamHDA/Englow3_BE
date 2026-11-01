package com.englow3.user.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.CertificateType;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Every field but the certificate is optional - sending them empty is how a learner skips this step. A certificate
 * learner aims at {@code targetScore}; anyone else may aim at {@code targetLevel} instead.
 */
public record SetLearningGoalRequest(@NotNull CertificateType certificateType, @Positive BigDecimal currentScore,
        @Positive BigDecimal targetScore, @Future LocalDate targetDate, CertificateLevel targetLevel,
        @Min(10) @Max(240) Integer dailyStudyMinutes) {
}
