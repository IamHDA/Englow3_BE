package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.UserRepository;

/**
 * The band table and the step transition are the two things that can be wrong here. Writing the profile is JPA's job
 * and is not re-asserted.
 */
class PlacementRecorderTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final LearnerProfileRepository learnerProfileRepo = mock(LearnerProfileRepository.class);
    private final User user = mock(User.class);

    private final PlacementRecorder recorder = new PlacementRecorder(userRepo, learnerProfileRepo);

    private final UUID userId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private LearnerProfile profile;

    @BeforeEach
    void setUp() {
        profile = LearnerProfile.forUser(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(learnerProfileRepo.findByUserId(userId)).thenReturn(Optional.of(profile));
    }

    @Nested
    class Bands {

        @Test
        void mapsEachPercentageToItsBand() {
            assertThat(PlacementRecorder.levelFor(new BigDecimal("100"))).isEqualTo(CertificateLevel.C2);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("90"))).isEqualTo(CertificateLevel.C2);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("89.9"))).isEqualTo(CertificateLevel.C1);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("75"))).isEqualTo(CertificateLevel.C1);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("60"))).isEqualTo(CertificateLevel.B2);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("45"))).isEqualTo(CertificateLevel.B1);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("25"))).isEqualTo(CertificateLevel.A2);
            assertThat(PlacementRecorder.levelFor(new BigDecimal("24.9"))).isEqualTo(CertificateLevel.A1);
            assertThat(PlacementRecorder.levelFor(BigDecimal.ZERO)).isEqualTo(CertificateLevel.A1);
        }

        /** A paper with no gradeable question scores null rather than zero - it must not crash the hand-off. */
        @Test
        void treatsAnUnscoredAttemptAsTheLowestLevel() {
            assertThat(PlacementRecorder.levelFor(null)).isEqualTo(CertificateLevel.A1);
        }
    }

    @Nested
    class Recording {

        @Test
        void writesTheMeasuredLevelAndTheAttemptItCameFrom() {
            when(user.getOnboardingStep()).thenReturn(OnboardingStep.CURRENT_LEVEL);

            recorder.record(userId, attemptId, new BigDecimal("62"));

            assertThat(profile.getCurrentLevel()).isEqualTo(CertificateLevel.B2);
            assertThat(profile.getPlacementAttemptId()).isEqualTo(attemptId);
        }

        @Test
        void movesOnboardingPastTheStepThisTestAnswers() {
            when(user.getOnboardingStep()).thenReturn(OnboardingStep.CURRENT_LEVEL);

            recorder.record(userId, attemptId, new BigDecimal("62"));

            verify(user).moveTo(OnboardingStep.LEARNING_GOAL);
        }

        /** Retaking the test later updates the level; it does not drag a finished learner back through onboarding. */
        @Test
        void leavesTheStepAloneForALearnerWhoIsPastIt() {
            when(user.getOnboardingStep()).thenReturn(OnboardingStep.COMPLETED);

            recorder.record(userId, attemptId, new BigDecimal("62"));

            assertThat(profile.getCurrentLevel()).isEqualTo(CertificateLevel.B2);
            verify(user, never()).moveTo(OnboardingStep.LEARNING_GOAL);
        }

        /** The exam is already scored by the time we get here - a missing profile must not roll that back. */
        @Test
        void staysSilentWhenTheUserRowIsMissing() {
            when(userRepo.findById(userId)).thenReturn(Optional.empty());

            recorder.record(userId, attemptId, new BigDecimal("62"));

            verify(learnerProfileRepo, never()).findByUserId(userId);
        }
    }
}
