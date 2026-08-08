package com.englow3.user.dto;

import java.util.UUID;

import com.englow3.user.entity.User;

public record MeResponse(
        UUID id,
        String email,
        String role,
        String fullName,
        String displayName,
        String avatarUrl,
        String bannerUrl,
        OnboardingStateResponse onboarding) {

    public static MeResponse of(User user, String avatarUrl, String bannerUrl, OnboardingStateResponse onboarding) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getFullName(),
                user.getDisplayName(),
                avatarUrl,
                bannerUrl,
                onboarding);
    }
}
