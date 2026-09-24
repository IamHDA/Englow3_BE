package com.englow3.user.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "learner_profiles")
@Getter
public class LearnerProfile extends BasePersistedEntity {

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

    /**
     * The attempt the declared level came from, when it came from a placement test rather than the learner's own
     * answer. A plain UUID and not a JPA relationship: {@code exam_attempts} belongs to the exam module, and the
     * database keeps the foreign key so the row cannot point at nothing.
     */
    @Column(name = "placement_attempt_id")
    private UUID placementAttemptId;

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

    /**
     * Records a level the learner was measured at rather than one they chose. Keeping the attempt id is what makes the
     * level auditable later - "B1 because of this paper on this date", not "B1 because someone said so".
     */
    public void recordPlacement(CertificateLevel level, UUID attemptId) {
        this.currentLevel = level;
        this.placementAttemptId = attemptId;
    }

    public void aimAtCertificate(CertificateType certificateType) {
        this.targetCertificateType = certificateType;
    }

    public void setGoal(CertificateType targetCertificateType, BigDecimal currentScore, BigDecimal targetScore,
            LocalDate targetDate) {
        this.targetCertificateType = targetCertificateType;
        this.currentScore = currentScore;
        this.targetScore = targetScore;
        this.targetDate = targetDate;
    }
}
