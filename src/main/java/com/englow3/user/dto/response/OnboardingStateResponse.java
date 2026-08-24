package com.englow3.user.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.CertificateType;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.TargetSkill;
import com.englow3.user.dto.result.OnboardingStateResult;

public record OnboardingStateResponse(OnboardingStep step, Set<Integer> learningPurposeIds, boolean certificateLearner,
        CertificateType targetCertificateType, CertificateLevel currentLevel, BigDecimal targetScore,
        LocalDate targetDate, Set<TargetSkill> targetSkills) {

    public static OnboardingStateResponse from(OnboardingStateResult result) {
        return new OnboardingStateResponse(result.step(), result.learningPurposeIds(), result.certificateLearner(),
                result.targetCertificateType(), result.currentLevel(), result.targetScore(), result.targetDate(),
                result.targetSkills());
    }
}
