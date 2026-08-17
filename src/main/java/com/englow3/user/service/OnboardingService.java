package com.englow3.user.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.dto.command.SelectLearningPurposesCommand;
import com.englow3.user.dto.command.SelectTargetSkillsCommand;
import com.englow3.user.dto.command.SetCertificateTargetCommand;
import com.englow3.user.dto.command.SetCurrentLevelCommand;
import com.englow3.user.dto.command.SetLearningGoalCommand;
import com.englow3.user.dto.result.LearningPurposeResult;
import com.englow3.user.dto.result.OnboardingStateResult;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.LearningPurpose;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.LearningPurposeRepository;
import com.englow3.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepo;
    private final LearnerProfileRepository learnerProfileRepo;
    private final LearningPurposeRepository learningPurposeRepo;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public OnboardingStateResult currentState() {
        User user = requireCurrentUser();
        return state(user, profileOf(user));
    }

    @Transactional(readOnly = true)
    public List<LearningPurposeResult> listLearningPurposes() {
        return learningPurposeRepo.findAll().stream().map(LearningPurposeResult::from).toList();
    }

    @Transactional
    public OnboardingStateResult selectLearningPurposes(SelectLearningPurposesCommand command) {
        User user = requireCurrentUser();
        if (learningPurposeRepo.findAllById(command.purposeIds()).size() != command.purposeIds().size()) {
            throw new NotFoundException("LEARNING_PURPOSE_NOT_FOUND", "One or more learning purposes do not exist");
        }

        user.selectLearningPurposes(command.purposeIds());
        user.moveTo(isCertificateLearner(user) ? OnboardingStep.CERTIFICATE_TARGET : OnboardingStep.CURRENT_LEVEL);

        return state(user, profileOf(user));
    }

    @Transactional
    public OnboardingStateResult setCertificateTarget(SetCertificateTargetCommand command) {
        User user = requireCurrentUser();
        if (!isCertificateLearner(user)) {
            throw new BadRequestException("CERTIFICATE_TARGET_NOT_APPLICABLE",
                    "Only a learner with the certificate purpose picks a certificate to aim for");
        }

        LearnerProfile profile = profileOf(user);
        profile.aimAtCertificate(command.certificateType());
        user.moveTo(OnboardingStep.CURRENT_LEVEL);

        return state(user, profile);
    }

    @Transactional
    public OnboardingStateResult setCurrentLevel(SetCurrentLevelCommand command) {
        User user = requireCurrentUser();
        if (command.level() == null) {
            throw levelAssessmentUnavailable(isCertificateLearner(user));
        }

        LearnerProfile profile = profileOf(user);
        profile.declareCurrentLevel(command.level());
        user.moveTo(OnboardingStep.LEARNING_GOAL);

        return state(user, profile);
    }

    @Transactional
    public OnboardingStateResult setLearningGoal(SetLearningGoalCommand command) {
        User user = requireCurrentUser();
        LearnerProfile profile = profileOf(user);
        if (profile.getCurrentLevel() == null) {
            throw new ConflictException("ONBOARDING_LEVEL_REQUIRED",
                    "The goal step opens only once the level is known");
        }
        if (command.targetScore() != null && !isCertificateLearner(user)) {
            throw new BadRequestException("TARGET_SCORE_NOT_APPLICABLE",
                    "Only a certificate learner has a score to aim at");
        }

        profile.setGoal(command.certificateType(), command.targetScore(), command.targetDate());
        user.moveTo(OnboardingStep.TARGET_SKILLS);

        return state(user, profile);
    }

    @Transactional
    public OnboardingStateResult selectTargetSkills(SelectTargetSkillsCommand command) {
        User user = requireCurrentUser();
        user.selectTargetSkills(command.skills() == null ? Set.of() : command.skills());
        return state(user, profileOf(user));
    }

    @Transactional
    public OnboardingStateResult complete() {
        User user = requireCurrentUser();
        LearnerProfile profile = profileOf(user);

        user.completeOnboarding(profile.getCurrentLevel(), isCertificateLearner(user),
                profile.getTargetCertificateType());

        return state(user, profile);
    }

    private User requireCurrentUser() {
        UUID authProviderId = currentUser.authProviderId();
        return userRepo.findByAuthProviderId(authProviderId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                "No user is linked to auth provider id %s".formatted(authProviderId)));
    }

    /** The profile row is not created at signup - the first onboarding write creates it. */
    private LearnerProfile profileOf(User user) {
        return learnerProfileRepo.findByUserId(user.getId())
                .orElseGet(() -> learnerProfileRepo.save(LearnerProfile.forUser(user.getId())));
    }

    private boolean isCertificateLearner(User user) {
        return learningPurposeRepo.findByPurposeCode(LearningPurpose.CERTIFICATE_CODE)
                .map(purpose -> user.getLearningPurposeIds().contains(purpose.getId())).orElse(false);
    }

    private ConflictException levelAssessmentUnavailable(boolean certificateLearner) {
        return certificateLearner
                ? new ConflictException("PLACEMENT_NOT_AVAILABLE",
                        "The placement test is not implemented yet - declare a level for now")
                : new ConflictException("QUIZ_NOT_AVAILABLE",
                        "The levelling quiz is not implemented yet - declare a level for now");
    }

    private OnboardingStateResult state(User user, LearnerProfile profile) {
        return OnboardingStateResult.of(user, profile, isCertificateLearner(user));
    }
}
