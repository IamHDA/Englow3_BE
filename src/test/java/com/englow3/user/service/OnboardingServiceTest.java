package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.LearningPurpose;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.LearningPurposeRepository;
import com.englow3.user.repository.UserRepository;

/**
 * Orchestration only. The rules inside User are covered by UserTest - this class
 * asserts which entity call the service makes, with which arguments, and how the
 * flow branches.
 */
class OnboardingServiceTest {

    private static final int CERTIFICATE_PURPOSE_ID = 1;
    private static final int COMMUNICATION_PURPOSE_ID = 2;

    private final UserRepository users = mock(UserRepository.class);
    private final LearnerProfileRepository profiles = mock(LearnerProfileRepository.class);
    private final LearningPurposeRepository learningPurposes = mock(LearningPurposeRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final OnboardingService service = new OnboardingService(users, profiles, learningPurposes, currentUser);

    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);
    private final LearnerProfile profile = LearnerProfile.forUser(UUID.randomUUID());

    @BeforeEach
    void authenticateAnExistingUser() {
        UUID authProviderId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authProviderId);
        when(users.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(profile));

        LearningPurpose certificate = mock(LearningPurpose.class);
        when(certificate.getId()).thenReturn(CERTIFICATE_PURPOSE_ID);
        when(learningPurposes.findByPurposeCode(LearningPurpose.CERTIFICATE_CODE))
                .thenReturn(Optional.of(certificate));
    }

    @Test
    void failsWhenTheJwtPointsAtNoUserRow() {
        when(users.findByAuthProviderId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(service::currentState)
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void createsTheProfileRowOnFirstUseBecauseSignupDoesNotCreateIt() {
        when(profiles.findByUserId(userId)).thenReturn(Optional.empty());
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.currentState();

        verify(profiles).save(any(LearnerProfile.class));
    }

    @Test
    void sendsACertificateLearnerToTheCertificateStep() {
        Set<Integer> purposeIds = Set.of(CERTIFICATE_PURPOSE_ID);
        when(learningPurposes.findAllById(purposeIds)).thenReturn(List.of(mock(LearningPurpose.class)));
        when(user.getLearningPurposeIds()).thenReturn(purposeIds);

        service.selectLearningPurposes(purposeIds);

        verify(user).selectLearningPurposes(purposeIds);
        verify(user).moveTo(OnboardingStep.CERTIFICATE_TARGET);
    }

    @Test
    void sendsEveryOtherLearnerStraightToTheLevelStep() {
        Set<Integer> purposeIds = Set.of(COMMUNICATION_PURPOSE_ID);
        when(learningPurposes.findAllById(purposeIds)).thenReturn(List.of(mock(LearningPurpose.class)));
        when(user.getLearningPurposeIds()).thenReturn(purposeIds);

        service.selectLearningPurposes(purposeIds);

        verify(user).moveTo(OnboardingStep.CURRENT_LEVEL);
    }

    @Test
    void rejectsALearningPurposeThatDoesNotExist() {
        Set<Integer> purposeIds = Set.of(404);
        when(learningPurposes.findAllById(purposeIds)).thenReturn(List.of());

        assertThatThrownBy(() -> service.selectLearningPurposes(purposeIds))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("LEARNING_PURPOSE_NOT_FOUND");
        verify(user, never()).selectLearningPurposes(anySet());
    }

    @Test
    void refusesACertificateTargetFromALearnerNotOnThatBranch() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(COMMUNICATION_PURPOSE_ID));

        assertThatThrownBy(() -> service.setCertificateTarget("IELTS"))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("CERTIFICATE_TARGET_NOT_APPLICABLE");
    }

    @Test
    void reportsThatTheQuizIsNotAvailableWhenANonCertificateLearnerDoesNotKnowTheirLevel() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(COMMUNICATION_PURPOSE_ID));

        assertThatThrownBy(() -> service.setCurrentLevel(null))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("QUIZ_NOT_AVAILABLE");
    }

    @Test
    void reportsThatThePlacementTestIsNotAvailableWhenACertificateLearnerDoesNotKnowTheirLevel() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(CERTIFICATE_PURPOSE_ID));

        assertThatThrownBy(() -> service.setCurrentLevel(null))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("PLACEMENT_NOT_AVAILABLE");
    }

    @Test
    void movesToTheGoalStepOnceALevelIsDeclared() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(COMMUNICATION_PURPOSE_ID));

        service.setCurrentLevel(CertificateLevel.B1);

        verify(user).moveTo(OnboardingStep.LEARNING_GOAL);
    }

    @Test
    void refusesTheGoalStepBeforeTheLevelIsKnown() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(COMMUNICATION_PURPOSE_ID));

        assertThatThrownBy(() -> service.setLearningGoal(null, null))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("ONBOARDING_LEVEL_REQUIRED");
    }

    @Test
    void rejectsAScoreGoalFromALearnerWithNoCertificateToAimAt() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(COMMUNICATION_PURPOSE_ID));
        profile.declareCurrentLevel(CertificateLevel.B1);

        assertThatThrownBy(() -> service.setLearningGoal(new BigDecimal("7.0"), LocalDate.now().plusMonths(6)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("TARGET_SCORE_NOT_APPLICABLE");
    }

    @Test
    void handsTheCompletionRuleTheLevelAndCertificateItMustJudge() {
        when(user.getLearningPurposeIds()).thenReturn(Set.of(CERTIFICATE_PURPOSE_ID));
        profile.declareCurrentLevel(CertificateLevel.B2);
        profile.aimAtCertificate("IELTS");

        service.complete();

        verify(user).completeOnboarding(CertificateLevel.B2, true, "IELTS");
    }
}
