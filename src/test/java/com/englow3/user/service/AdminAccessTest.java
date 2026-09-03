package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

class AdminAccessTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final AdminAccess adminAccess = new AdminAccess(userRepo, currentUser);

    private final UUID authProviderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);

    @BeforeEach
    void authenticate() {
        when(currentUser.authProviderId()).thenReturn(authProviderId);
    }

    @Test
    void handsBackTheInternalIdOfAnAdmin() {
        signInAs(Role.ADMIN);

        assertThat(adminAccess.requireAdminId()).isEqualTo(userId);
    }

    @Test
    void refusesALearner() {
        signInAs(Role.LEARNER);

        assertThatThrownBy(adminAccess::requireAdminId).isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getCode()).isEqualTo("ADMIN_ONLY");
    }

    @Test
    void failsWhenTheJwtPointsAtNoUserRow() {
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.empty());

        assertThatThrownBy(adminAccess::requireAdminId).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND");
    }

    private void signInAs(Role role) {
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn(role);
        when(user.getId()).thenReturn(userId);
    }
}
