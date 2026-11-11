package com.englow3.user.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
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

    /** The CEFR band a learner who is not preparing for a certificate is aiming at. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_level")
    private CertificateLevel targetLevel;

    @Column(name = "daily_study_minutes")
    private Integer dailyStudyMinutes;

    /**
     * The roadmap level check the level came from, when it was measured that way rather than by the placement paper. A
     * plain id: level checks belong to the roadmap module.
     */
    @Column(name = "level_check_id")
    private UUID levelCheckId;
    private Instant levelMeasuredAt;
    private boolean levelMeasurementFinal;
    @jakarta.persistence.Version
    private long version;

    static final int MIN_DAILY_MINUTES = 10;
    static final int MAX_DAILY_MINUTES = 240;

    protected LearnerProfile() {
    }

    public static LearnerProfile forUser(UUID userId) {
        LearnerProfile profile = new LearnerProfile();
        profile.id = UUID.randomUUID();
        profile.userId = userId;
        return profile;
    }

    /**
     * A level the learner chose. Any placement on record no longer describes it, so the link goes: kept, it would
     * present a declared C1 as "measured by the placement test" to everything that asks how the level was set.
     */
    public void declareCurrentLevel(CertificateLevel level) {
        declareCurrentLevel(level, Instant.EPOCH);
    }

    public void declareCurrentLevel(CertificateLevel level, Instant declaredAt) {
        this.currentLevel = level;
        this.placementAttemptId = null;
        this.levelCheckId = null;
        this.currentScore = null;
        this.levelMeasuredAt = declaredAt;
        this.levelMeasurementFinal = false;
    }

    /** Grades are ordered by when the test began, never by when its callback happened to arrive. */
    public boolean acceptMeasurement(UUID source, Instant measuredAt, boolean finalResult) {
        java.util.Objects.requireNonNull(measuredAt, "A measurement needs its original time");
        if (levelMeasuredAt != null) {
            int order = measuredAt.compareTo(levelMeasuredAt);
            if (order < 0)
                return false;
            if (order == 0 && (!source.equals(placementAttemptId) && !source.equals(levelCheckId)
                    || levelMeasurementFinal && !finalResult))
                return false;
        }
        levelMeasuredAt = measuredAt;
        levelMeasurementFinal = finalResult;
        return true;
    }

    /**
     * Records a level the learner was measured at rather than one they chose. Keeping the attempt id is what makes the
     * level auditable later - "B1 because of this paper on this date", not "B1 because someone said so".
     */
    public void recordPlacement(CertificateLevel level, UUID attemptId) {
        this.currentLevel = level;
        this.placementAttemptId = attemptId;
        this.levelCheckId = null;
    }

    /** A placement that also measured a certificate score - an IELTS band from the full test. */
    public void recordPlacement(CertificateLevel level, UUID attemptId, BigDecimal score) {
        recordPlacement(level, attemptId);
        this.currentScore = score;
    }

    /** A level measured by the roadmap's level check; the counterpart of {@link #recordPlacement}. */
    public void recordLevelCheck(CertificateLevel level, UUID levelCheckId) {
        this.currentLevel = level;
        this.levelCheckId = levelCheckId;
        this.placementAttemptId = null;
    }

    public void aimAtCertificate(CertificateType certificateType) {
        if (this.targetCertificateType != certificateType) {
            this.currentScore = null;
            this.targetScore = null;
        }
        this.targetCertificateType = certificateType;
    }

    /**
     * A goal is either a certificate score or a CEFR level, never both: a plan cannot aim at two finishing lines, and
     * the roadmap decides how to measure progress by which one is set.
     */
    public void setGoal(CertificateType targetCertificateType, BigDecimal currentScore, BigDecimal targetScore,
            LocalDate targetDate, CertificateLevel targetLevel, Integer dailyStudyMinutes) {
        if (targetLevel != null && targetScore != null) {
            throw new BadRequestException("GOAL_SCORE_AND_LEVEL",
                    "Aim at a certificate score or at a level, not at both");
        }
        if (targetLevel != null && currentLevel != null && targetLevel.compareTo(currentLevel) <= 0) {
            throw new BadRequestException("TARGET_LEVEL_NOT_ABOVE_CURRENT",
                    "The target level must be higher than the current level");
        }
        if (dailyStudyMinutes != null
                && (dailyStudyMinutes < MIN_DAILY_MINUTES || dailyStudyMinutes > MAX_DAILY_MINUTES)) {
            throw new BadRequestException("DAILY_MINUTES_OUT_OF_RANGE",
                    "Daily study time must be between 10 and 240 minutes");
        }
        this.targetCertificateType = targetCertificateType;
        this.currentScore = currentScore;
        this.targetScore = targetScore;
        this.targetDate = targetDate;
        this.targetLevel = targetLevel;
        this.dailyStudyMinutes = dailyStudyMinutes;
    }
}
