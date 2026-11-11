package com.englow3.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class LearnerProfileTest {
    @Test
    void delayedAndProvisionalMeasurementsCannotReplaceNewerOrFinalOnes() {
        UUID old = UUID.randomUUID(), newer = UUID.randomUUID();
        var time = java.time.Instant.parse("2026-11-10T00:00:00Z");
        assertThat(profile.acceptMeasurement(old, time, false)).isTrue();
        profile.recordPlacement(CertificateLevel.B1, old, new BigDecimal("5"));
        assertThat(profile.acceptMeasurement(newer, time.plusSeconds(1), false)).isTrue();
        profile.recordPlacement(CertificateLevel.B2, newer, new BigDecimal("6"));
        assertThat(profile.acceptMeasurement(old, time, true)).isFalse();
        assertThat(profile.acceptMeasurement(newer, time.plusSeconds(1), true)).isTrue();
        profile.recordPlacement(CertificateLevel.C1, newer, new BigDecimal("7"));
        assertThat(profile.acceptMeasurement(newer, time.plusSeconds(1), false)).isFalse();
        assertThat(profile.getCurrentScore()).isEqualByComparingTo("7");
    }

    @Test
    void declaringALevelDiscardsTheOldMeasuredScore() {
        profile.recordPlacement(CertificateLevel.B2, UUID.randomUUID(), new BigDecimal("6"));
        profile.declareCurrentLevel(CertificateLevel.C1, java.time.Instant.now());
        assertThat(profile.getCurrentScore()).isNull();
    }

    private final LearnerProfile profile = LearnerProfile.forUser(UUID.randomUUID());

    @Test
    void aimsAtALevelWithAStudyTime() {
        profile.declareCurrentLevel(CertificateLevel.A2);

        profile.setGoal(null, null, null, null, CertificateLevel.B1, 30);

        assertThat(profile.getTargetLevel()).isEqualTo(CertificateLevel.B1);
        assertThat(profile.getDailyStudyMinutes()).isEqualTo(30);
    }

    @Test
    void refusesAScoreAndALevelTogether() {
        assertThatThrownBy(() -> profile.setGoal(CertificateType.IELTS, null, new BigDecimal("6.5"), null,
                CertificateLevel.C1, null)).isInstanceOf(BadRequestException.class)
                        .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("GOAL_SCORE_AND_LEVEL");
    }

    @Test
    void refusesATargetLevelThatIsNotAboveTheCurrentOne() {
        profile.declareCurrentLevel(CertificateLevel.B2);

        assertThatThrownBy(() -> profile.setGoal(null, null, null, null, CertificateLevel.B2, null))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("TARGET_LEVEL_NOT_ABOVE_CURRENT");
    }

    @Test
    void refusesAStudyTimeOutsideTenMinutesToFourHours() {
        assertThatThrownBy(() -> profile.setGoal(null, null, null, null, null, 5))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> profile.setGoal(null, null, null, null, null, 300))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aLevelCheckReplacesAPlacementAsTheSourceOfTheLevel() {
        profile.recordPlacement(CertificateLevel.B1, UUID.randomUUID());
        UUID levelCheck = UUID.randomUUID();

        profile.recordLevelCheck(CertificateLevel.B2, levelCheck);

        assertThat(profile.getCurrentLevel()).isEqualTo(CertificateLevel.B2);
        assertThat(profile.getLevelCheckId()).isEqualTo(levelCheck);
        assertThat(profile.getPlacementAttemptId()).isNull();
    }

    @Test
    void aDeclaredLevelHasNoMeasurementBehindIt() {
        profile.recordLevelCheck(CertificateLevel.B2, UUID.randomUUID());

        profile.declareCurrentLevel(CertificateLevel.C1);

        assertThat(profile.getLevelCheckId()).isNull();
        assertThat(profile.getPlacementAttemptId()).isNull();
    }
}
