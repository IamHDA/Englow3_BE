package com.englow3.user.dto.result;

import java.time.LocalDate;
import java.util.UUID;

import com.englow3.user.entity.Gender;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.User;

public record UserInformationResult(UUID id, String email, String fullName, String displayName, Gender gender,
        LocalDate birthDate, String avatarObjectKey, String bannerObjectKey, OnboardingStep onboardingStep) {

    public static UserInformationResult of(User user) {
        return new UserInformationResult(user.getId(), user.getEmail(), user.getFullName(), user.getDisplayName(),
                user.getGender(), user.getBirthDate(), user.getAvatarObjectKey(), user.getBannerObjectKey(),
                user.getOnboardingStep());
    }
}
