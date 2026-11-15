package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.service.impl.PublicProfileReaderImpl;

class PublicProfileReaderImplTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final PublicProfileReader reader = new PublicProfileReaderImpl(userRepo, "https://cdn.example/avatars",
            "/avatars");

    private static User user(UUID id, String displayName, String avatarKey) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getDisplayName()).thenReturn(displayName);
        when(user.getAvatarObjectKey()).thenReturn(avatarKey);
        return user;
    }

    @Test
    void aPresetAvatarIsServedFromTheWebAppsOwnFiles() {
        UUID id = UUID.randomUUID();
        User fox = user(id, "An", "preset:fox");
        when(userRepo.findAllById(Set.of(id))).thenReturn(List.of(fox));

        assertThat(reader.profiles(Set.of(id)).get(id)).isEqualTo(new PublicProfile(id, "An", "/avatars/fox.svg"));
    }

    @Test
    void anUploadedAvatarIsServedFromTheBucket() {
        UUID id = UUID.randomUUID();
        User uploaded = user(id, "An", "users/abc.png");
        when(userRepo.findAllById(Set.of(id))).thenReturn(List.of(uploaded));

        assertThat(reader.profiles(Set.of(id)).get(id).avatarUrl())
                .isEqualTo("https://cdn.example/avatars/users/abc.png");
    }

    @Test
    void noAvatarIsNoUrl() {
        UUID id = UUID.randomUUID();
        User plain = user(id, "An", null);
        when(userRepo.findAllById(Set.of(id))).thenReturn(List.of(plain));

        assertThat(reader.profiles(Set.of(id)).get(id).avatarUrl()).isNull();
    }

    @Test
    void idsWithNoUserAreAbsentFromTheMap() {
        UUID known = UUID.randomUUID();
        UUID erased = UUID.randomUUID();
        User survivor = user(known, "An", null);
        when(userRepo.findAllById(Set.of(known, erased))).thenReturn(List.of(survivor));

        Map<UUID, PublicProfile> profiles = reader.profiles(Set.of(known, erased));

        assertThat(profiles).containsOnlyKeys(known);
    }

    @Test
    void noIdsMeansNoQuery() {
        assertThat(reader.profiles(Set.of())).isEmpty();
        verifyNoInteractions(userRepo);
    }

    @Test
    void isLearnerAsksForTheLearnerRoleOnly() {
        UUID id = UUID.randomUUID();
        when(userRepo.existsByIdAndRole(id, Role.LEARNER)).thenReturn(true);

        assertThat(reader.isLearner(id)).isTrue();
        assertThat(reader.isLearner(UUID.randomUUID())).isFalse();
    }

    @Test
    void searchTrimsLowerCasesAndEscapesLikeCharactersSoTheyMatchLiterally() {
        UUID excluded = UUID.randomUUID();
        Pageable page = PageRequest.of(0, 20);
        when(userRepo.searchLearners(any(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<PublicProfile> result = reader.searchLearners("  50%_Off! ", Set.of(excluded), page);

        assertThat(result).isEmpty();
        verify(userRepo).searchLearners(eq("%50!%!_off!!%"), eq(Set.of(excluded)), eq(page));
    }
}
