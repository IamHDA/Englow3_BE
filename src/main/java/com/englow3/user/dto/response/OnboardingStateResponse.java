package com.englow3.user.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.TargetSkill;
import com.englow3.user.entity.User;

public record OnboardingStateResponse(OnboardingStep step, boolean completed, Set<Integer> learningPurposeIds,
        boolean certificateLearner, String targetCertificateType, CertificateLevel currentLevel, BigDecimal targetScore,
        LocalDate targetDate, Set<TargetSkill> targetSkills) {

    public static OnboardingStateResponse of(User user, LearnerProfile profile, boolean certificateLearner) {
        return new OnboardingStateResponse(user.getOnboardingStep(), user.isOnboardingCompleted(),
                new TreeSet<>(user.getLearningPurposeIds()), certificateLearner, profile.getTargetCertificateType(),
                profile.getCurrentLevel(), profile.getTargetScore(), profile.getTargetDate(),
                new TreeSet<>(user.getTargetSkills()));
    }
}
