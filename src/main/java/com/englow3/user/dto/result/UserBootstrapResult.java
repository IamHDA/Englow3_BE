package com.englow3.user.dto.result;

/** A single owner-scoped account snapshot. Back-office accounts have no learner onboarding state. */
public record UserBootstrapResult(UserInformationResult user, OnboardingStateResult onboardingState) {
}
