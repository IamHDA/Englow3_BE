package com.englow3.user.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.CertificateType;

public record SetLearningGoalCommand(CertificateType certificateType, BigDecimal currentScore, BigDecimal targetScore,
        LocalDate targetDate, CertificateLevel targetLevel, Integer dailyStudyMinutes) {

    /** A certificate goal with no level target and no stated study time. */
    public SetLearningGoalCommand(CertificateType certificateType, BigDecimal currentScore, BigDecimal targetScore,
            LocalDate targetDate) {
        this(certificateType, currentScore, targetScore, targetDate, null, null);
    }
}
