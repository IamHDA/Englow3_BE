package com.englow3.user.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.englow3.user.dto.result.UserInformationResult;
import com.englow3.user.entity.Gender;
import com.englow3.user.entity.OnboardingStep;

public record UserInformationResponse(UUID id, String email, String fullName, String displayName, Gender gender,
        LocalDate birthDate, String avatarUrl, String bannerUrl, OnboardingStep onboardingStep) {

    public static UserInformationResponse from(UserInformationResult result, String publicBaseUrl) {
        return new UserInformationResponse(result.id(), result.email(), result.fullName(), result.displayName(),
                result.gender(), result.birthDate(), publicUrl(publicBaseUrl, result.avatarObjectKey()),
                publicUrl(publicBaseUrl, result.bannerObjectKey()), result.onboardingStep());
    }

    /** The stored key is the whole path, so the URL is one concatenation - nothing is rebuilt from parts. */
    private static String publicUrl(String publicBaseUrl, String objectKey) {
        return objectKey == null ? null : publicBaseUrl + "/" + objectKey;
    }
}
