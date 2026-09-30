package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.repository.UserTourCompletionRepository;
import com.englow3.user.service.impl.UserTourServiceImpl;

class UserTourServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final UserTourCompletionRepository tourRepo = mock(UserTourCompletionRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UserTourService service = new UserTourServiceImpl(userRepo, tourRepo, currentUser);
    private final UUID authId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);

    @BeforeEach
    void authenticate() {
        when(currentUser.authProviderId()).thenReturn(authId);
        when(userRepo.findByAuthProviderId(authId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(Role.STAFF);
    }

    @Test
    void checksTheCurrentUsersRoleRatherThanAnInputRole() {
        when(tourRepo.existsByUserIdAndRoleAndVersion(userId, Role.STAFF, 1)).thenReturn(true);

        assertThat(service.completed()).isTrue();
        verify(tourRepo).existsByUserIdAndRoleAndVersion(userId, Role.STAFF, 1);
    }

    @Test
    void recordsCompletionOnlyForTheAuthenticatedRole() {
        service.complete();

        verify(tourRepo).insertIfAbsent(any(UUID.class), eq(userId), eq("STAFF"), eq(1));
    }

    @Test
    void refusesAnAccountWithoutALinkedUser() {
        when(userRepo.findByAuthProviderId(authId)).thenReturn(Optional.empty());

        assertThatThrownBy(service::complete).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(tourRepo);
    }
}
