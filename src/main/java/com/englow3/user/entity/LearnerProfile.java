package com.englow3.user.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "learner_profiles")
@Getter
public class LearnerProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level")
    private CertificateLevel currentLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_certificate_type")
    private CertificateType targetCertificateType;

    @Column(name = "current_score")
    private BigDecimal currentScore;

    @Column(name = "target_score")
    private BigDecimal targetScore;

    @Column(name = "target_date")
    private LocalDate targetDate;

    protected LearnerProfile() {
    }

    public static LearnerProfile forUser(UUID userId) {
        LearnerProfile profile = new LearnerProfile();
        profile.id = UUID.randomUUID();
        profile.userId = userId;
        return profile;
    }

    public void declareCurrentLevel(CertificateLevel level) {
        this.currentLevel = level;
    }

    public void aimAtCertificate(CertificateType certificateType) {
        this.targetCertificateType = certificateType;
    }

    public void setGoal(CertificateType targetCertificateType, BigDecimal targetScore, LocalDate targetDate) {
        this.targetCertificateType = targetCertificateType;
        this.targetScore = targetScore;
        this.targetDate = targetDate;
    }
}
