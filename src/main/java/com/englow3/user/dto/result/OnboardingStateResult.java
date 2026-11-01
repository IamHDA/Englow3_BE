package com.englow3.user.dto.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.CertificateType;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.TargetSkill;
import com.englow3.user.entity.User;

/**
 * @param placementAttemptId
 *            the placement attempt the current level was measured by; null when the learner chose the level themselves
 *            or has not taken the test
 */
public record OnboardingStateResult(OnboardingStep step, Set<Integer> learningPurposeIds, boolean certificateLearner,
        CertificateType targetCertificateType, CertificateLevel currentLevel, BigDecimal targetScore,
        LocalDate targetDate, Set<TargetSkill> targetSkills, CertificateLevel targetLevel, Integer dailyStudyMinutes,
        UUID placementAttemptId) {

    public static OnboardingStateResult of(User user, LearnerProfile profile, boolean certificateLearner) {
        return new OnboardingStateResult(user.getOnboardingStep(), new TreeSet<>(user.getLearningPurposeIds()),
                certificateLearner, profile.getTargetCertificateType(), profile.getCurrentLevel(),
                profile.getTargetScore(), profile.getTargetDate(), new TreeSet<>(user.getTargetSkills()),
                profile.getTargetLevel(), profile.getDailyStudyMinutes(), profile.getPlacementAttemptId());
    }
}
