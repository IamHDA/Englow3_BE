package com.englow3.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class LearnerProfileTest {

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
