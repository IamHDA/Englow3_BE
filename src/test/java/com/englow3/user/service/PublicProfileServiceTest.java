package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.englow3.shared.error.NotFoundException;
import com.englow3.user.dto.result.PublicProfileResult;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.service.impl.PublicProfileServiceImpl;

class PublicProfileServiceTest {

    private static final Instant JOINED = Instant.parse("2026-01-02T03:04:05Z");

    private final UserRepository userRepo = mock(UserRepository.class);
    private final PublicProfileService service = new PublicProfileServiceImpl(userRepo, "https://cdn.example/avatars",
            "/avatars");
    private final UUID id = UUID.randomUUID();

    private User account(Role role, String avatarKey, String bannerKey) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getDisplayName()).thenReturn("An");
        when(user.getRole()).thenReturn(role);
        when(user.getAvatarObjectKey()).thenReturn(avatarKey);
        when(user.getBannerObjectKey()).thenReturn(bannerKey);
        when(user.getCreatedAt()).thenReturn(JOINED);
        return user;
    }

    @Test
    void aLearnerIsNotStaffAndShowsOnlyTheBasics() {
        User learner = account(Role.LEARNER, "preset:fox", "users/banner.png");
        when(userRepo.findById(id)).thenReturn(Optional.of(learner));

        PublicProfileResult result = service.wall(id);

        assertThat(result.staff()).isFalse();
        assertThat(result.userId()).isEqualTo(id);
        assertThat(result.displayName()).isEqualTo("An");
        assertThat(result.avatarUrl()).isEqualTo("/avatars/fox.svg");
        assertThat(result.bannerUrl()).isEqualTo("https://cdn.example/avatars/users/banner.png");
        assertThat(result.joinedAt()).isEqualTo(JOINED);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = { "STAFF", "ADMIN" })
    void staffAndAdminAreMarkedStaff(Role role) {
        User account = account(role, null, null);
        when(userRepo.findById(id)).thenReturn(Optional.of(account));

        assertThat(service.wall(id).staff()).isTrue();
    }

    @Test
    void noBannerAndNoAvatarMeanNoUrls() {
        User plain = account(Role.LEARNER, null, null);
        when(userRepo.findById(id)).thenReturn(Optional.of(plain));

        PublicProfileResult result = service.wall(id);

        assertThat(result.bannerUrl()).isNull();
        assertThat(result.avatarUrl()).isNull();
    }

    @Test
    void anUploadedAvatarIsServedFromTheBucket() {
        User uploaded = account(Role.LEARNER, "users/abc.png", null);
        when(userRepo.findById(id)).thenReturn(Optional.of(uploaded));

        assertThat(service.wall(id).avatarUrl()).isEqualTo("https://cdn.example/avatars/users/abc.png");
    }

    @Test
    void anAccountThatDoesNotExistIsNotFound() {
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.wall(id)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("USER_NOT_FOUND"));
    }
}
