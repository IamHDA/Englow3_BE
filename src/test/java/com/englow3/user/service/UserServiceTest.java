package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.dto.MeResponse;
import com.englow3.user.dto.OnboardingStateResponse;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

class UserServiceTest {

    private static final String PUBLIC_BASE_URL = "http://localhost:9000/englow3";

    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final OnboardingService onboardingService = mock(OnboardingService.class);

    private final UserService service = new UserService(users, currentUser, onboardingService, PUBLIC_BASE_URL);

    private final User user = mock(User.class);

    @BeforeEach
    void authenticateAnExistingUser() {
        UUID authProviderId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authProviderId);
        when(users.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));
        when(onboardingService.currentState()).thenReturn(onboardingState());
    }

    @Test
    void returnsTheIdentityWithTheOnboardingStateNested() {
        UUID id = UUID.randomUUID();
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("learner@example.com");
        when(user.getRole()).thenReturn("LEARNER");
        when(user.getDisplayName()).thenReturn("Duy Anh");

        MeResponse response = service.me();

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("learner@example.com");
        assertThat(response.role()).isEqualTo("LEARNER");
        assertThat(response.displayName()).isEqualTo("Duy Anh");
        assertThat(response.onboarding().step()).isEqualTo(OnboardingStep.CURRENT_LEVEL);
    }

    @Test
    void buildsPublicUrlsFromTheStoredObjectKeys() {
        when(user.getAvatarObjectKey()).thenReturn("avatars/6f1c.png");
        when(user.getBannerObjectKey()).thenReturn("banners/6f1c.jpg");

        MeResponse response = service.me();

        assertThat(response.avatarUrl()).isEqualTo(PUBLIC_BASE_URL + "/avatars/6f1c.png");
        assertThat(response.bannerUrl()).isEqualTo(PUBLIC_BASE_URL + "/banners/6f1c.jpg");
    }

    @Test
    void leavesImageUrlsNullWhenTheUserUploadedNothing() {
        when(user.getAvatarObjectKey()).thenReturn(null);
        when(user.getBannerObjectKey()).thenReturn(null);

        MeResponse response = service.me();

        assertThat(response.avatarUrl()).isNull();
        assertThat(response.bannerUrl()).isNull();
    }

    @Test
    void failsWhenTheJwtPointsAtNoUserRow() {
        when(users.findByAuthProviderId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(service::me)
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    private OnboardingStateResponse onboardingState() {
        return new OnboardingStateResponse(OnboardingStep.CURRENT_LEVEL, false, Set.of(1), true,
                "IELTS", null, null, null, Set.of());
    }
}
