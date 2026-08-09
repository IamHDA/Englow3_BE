package com.englow3.user.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.englow3.user.entity.Gender;
import com.englow3.user.entity.User;

public record UserInformationResponse(UUID id, String email, String fullName, String displayName, Gender gender,
        LocalDate birthDate, String avatarUrl, String bannerUrl) {

    public static UserInformationResponse of(User user, String publicBaseUrl) {
        return new UserInformationResponse(user.getId(), user.getEmail(), user.getFullName(), user.getDisplayName(),
                user.getGender(), user.getBirthDate(), publicUrl(publicBaseUrl, user.getAvatarObjectKey()),
                publicUrl(publicBaseUrl, user.getBannerObjectKey()));
    }

    /** The stored key is the whole path, so the URL is one concatenation - nothing is rebuilt from parts. */
    private static String publicUrl(String publicBaseUrl, String objectKey) {
        return objectKey == null ? null : publicBaseUrl + "/" + objectKey;
    }
}
