package com.englow3.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.ConflictException;

class UserTest {

    @Test
    void refusesToCompleteWithoutAnyLearningPurpose() {
        User user = new User();

        assertThatThrownBy(() -> user.completeOnboarding(CertificateLevel.B1, false, null))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("ONBOARDING_PURPOSE_REQUIRED");
        assertThat(user.getOnboardingStep()).isNotEqualTo(OnboardingStep.COMPLETED);
    }

    @Test
    void refusesToCompleteWithoutAKnownLevel() {
        User user = new User();
        user.selectLearningPurposes(Set.of(1));

        assertThatThrownBy(() -> user.completeOnboarding(null, false, null)).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("ONBOARDING_LEVEL_REQUIRED");
        assertThat(user.getOnboardingStep()).isNotEqualTo(OnboardingStep.COMPLETED);
    }

    @Test
    void refusesToCompleteWhenACertificateLearnerPickedNoCertificate() {
        User user = new User();
        user.selectLearningPurposes(Set.of(1));

        assertThatThrownBy(() -> user.completeOnboarding(CertificateLevel.B1, true, null))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("ONBOARDING_CERTIFICATE_TARGET_REQUIRED");
        assertThat(user.getOnboardingStep()).isNotEqualTo(OnboardingStep.COMPLETED);
    }

    @Test
    void completesWhenPurposeAndLevelArePresent() {
        User user = new User();
        user.selectLearningPurposes(Set.of(1, 2));

        user.completeOnboarding(CertificateLevel.B2, true, "IELTS");

        assertThat(user.getOnboardingStep()).isEqualTo(OnboardingStep.COMPLETED);
    }

    @Test
    void rejectsAnEmptyLearningPurposeSelection() {
        User user = new User();

        assertThatThrownBy(() -> user.selectLearningPurposes(Set.of())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("ONBOARDING_PURPOSE_REQUIRED");
    }

    @Test
    void replacesTheWholeLearningPurposeSelectionOnEachCall() {
        User user = new User();
        user.selectLearningPurposes(Set.of(1, 2));

        user.selectLearningPurposes(Set.of(3));

        assertThat(user.getLearningPurposeIds()).containsExactly(3);
    }

    @Test
    void acceptsAnEmptyTargetSkillSelectionAsDontKnow() {
        User user = new User();
        user.selectTargetSkills(Set.of(TargetSkill.READING));

        user.selectTargetSkills(Set.of());

        assertThat(user.getTargetSkills()).isEmpty();
    }
}
