package com.englow3.user.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.dto.LearningPurposeResponse;
import com.englow3.user.dto.OnboardingStateResponse;
import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.LearningPurpose;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.TargetSkill;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.LearningPurposeRepository;
import com.englow3.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository users;
    private final LearnerProfileRepository profiles;
    private final LearningPurposeRepository learningPurposes;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public OnboardingStateResponse currentState() {
        User user = requireCurrentUser();
        return state(user, profileOf(user));
    }

    @Transactional(readOnly = true)
    public List<LearningPurposeResponse> listLearningPurposes() {
        return learningPurposes.findAll().stream().map(LearningPurposeResponse::from).toList();
    }

    @Transactional
    public OnboardingStateResponse selectLearningPurposes(Set<Integer> purposeIds) {
        User user = requireCurrentUser();
        if (learningPurposes.findAllById(purposeIds).size() != purposeIds.size()) {
            throw new NotFoundException("LEARNING_PURPOSE_NOT_FOUND", "One or more learning purposes do not exist");
        }

        user.selectLearningPurposes(purposeIds);
        user.moveTo(isCertificateLearner(user) ? OnboardingStep.CERTIFICATE_TARGET : OnboardingStep.CURRENT_LEVEL);

        return state(user, profileOf(user));
    }

    @Transactional
    public OnboardingStateResponse setCertificateTarget(String certificateType) {
        User user = requireCurrentUser();
        if (!isCertificateLearner(user)) {
            throw new BadRequestException("CERTIFICATE_TARGET_NOT_APPLICABLE",
                    "Only a learner with the certificate purpose picks a certificate to aim for");
        }

        LearnerProfile profile = profileOf(user);
        profile.aimAtCertificate(certificateType);
        user.moveTo(OnboardingStep.CURRENT_LEVEL);

        return state(user, profile);
    }

    @Transactional
    public OnboardingStateResponse setCurrentLevel(CertificateLevel level) {
        User user = requireCurrentUser();
        if (level == null) {
            throw levelAssessmentUnavailable(isCertificateLearner(user));
        }

        LearnerProfile profile = profileOf(user);
        profile.declareCurrentLevel(level);
        user.moveTo(OnboardingStep.LEARNING_GOAL);

        return state(user, profile);
    }

    @Transactional
    public OnboardingStateResponse setLearningGoal(BigDecimal targetScore, LocalDate targetDate) {
        User user = requireCurrentUser();
        LearnerProfile profile = profileOf(user);
        if (profile.getCurrentLevel() == null) {
            throw new ConflictException("ONBOARDING_LEVEL_REQUIRED", "The goal step opens only once the level is known");
        }
        if (targetScore != null && !isCertificateLearner(user)) {
            throw new BadRequestException("TARGET_SCORE_NOT_APPLICABLE",
                    "Only a certificate learner has a score to aim at");
        }

        profile.setGoal(targetScore, targetDate);
        user.moveTo(OnboardingStep.TARGET_SKILLS);

        return state(user, profile);
    }

    @Transactional
    public OnboardingStateResponse selectTargetSkills(Set<TargetSkill> skills) {
        User user = requireCurrentUser();
        user.selectTargetSkills(skills == null ? Set.of() : skills);
        return state(user, profileOf(user));
    }

    @Transactional
    public OnboardingStateResponse complete() {
        User user = requireCurrentUser();
        LearnerProfile profile = profileOf(user);

        user.completeOnboarding(profile.getCurrentLevel(), isCertificateLearner(user),
                profile.getTargetCertificateType());

        return state(user, profile);
    }

    private User requireCurrentUser() {
        UUID authProviderId = currentUser.authProviderId();
        return users.findByAuthProviderId(authProviderId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user is linked to auth provider id %s".formatted(authProviderId)));
    }

    /** The profile row is not created at signup - the first onboarding write creates it. */
    private LearnerProfile profileOf(User user) {
        return profiles.findByUserId(user.getId())
                .orElseGet(() -> profiles.save(LearnerProfile.forUser(user.getId())));
    }

    private boolean isCertificateLearner(User user) {
        return learningPurposes.findByPurposeCode(LearningPurpose.CERTIFICATE_CODE)
                .map(purpose -> user.getLearningPurposeIds().contains(purpose.getId()))
                .orElse(false);
    }

    private ConflictException levelAssessmentUnavailable(boolean certificateLearner) {
        return certificateLearner
                ? new ConflictException("PLACEMENT_NOT_AVAILABLE",
                        "The placement test is not implemented yet - declare a level for now")
                : new ConflictException("QUIZ_NOT_AVAILABLE",
                        "The levelling quiz is not implemented yet - declare a level for now");
    }

    private OnboardingStateResponse state(User user, LearnerProfile profile) {
        return OnboardingStateResponse.of(user, profile, isCertificateLearner(user));
    }
}
