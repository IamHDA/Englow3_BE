package com.englow3.user.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.dto.MeResponse;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final OnboardingService onboardingService;
    private final String publicBaseUrl;

    UserService(UserRepository users, CurrentUser currentUser, OnboardingService onboardingService,
            @Value("${app.storage.public-base-url}") String publicBaseUrl) {
        this.users = users;
        this.currentUser = currentUser;
        this.onboardingService = onboardingService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public MeResponse me() {
        User user = requireCurrentUser();
        return MeResponse.of(user,
                publicUrl(user.getAvatarObjectKey()),
                publicUrl(user.getBannerObjectKey()),
                onboardingService.currentState());
    }

    /** Avatars and banners are public objects, so a stable URL beats a signed one that expires mid-session. */
    private String publicUrl(String objectKey) {
        return objectKey == null ? null : publicBaseUrl + "/" + objectKey;
    }

    private User requireCurrentUser() {
        UUID authProviderId = currentUser.authProviderId();
        return users.findByAuthProviderId(authProviderId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user is linked to auth provider id %s".formatted(authProviderId)));
    }
}
