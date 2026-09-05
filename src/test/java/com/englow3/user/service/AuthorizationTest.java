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
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

class AuthorizationTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final Authorization authorization = new Authorization(userRepo, currentUser);

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

        assertThat(authorization.requireAdminId()).isEqualTo(userId);
    }

    @Test
    void refusesALearnerWithTheSameCodeThePreAuthorizePathUses() {
        signInAs(Role.LEARNER);

        assertThatThrownBy(authorization::requireAdminId).isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getCode()).isEqualTo("ACCESS_DENIED");
    }

    /** Staff is not admin - the ladder only runs upward. */
    @Test
    void refusesStaffTheAdminGate() {
        signInAs(Role.STAFF);

        assertThatThrownBy(authorization::requireAdminId).isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getCode()).isEqualTo("ACCESS_DENIED");
    }

    /** A token with no user row behind it is a sync defect, but the caller still just gets refused. */
    @Test
    void refusesWhenTheTokenPointsAtNoUserRow() {
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.empty());

        assertThatThrownBy(authorization::requireAdminId).isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).getCode()).isEqualTo("ACCESS_DENIED");
        assertThat(authorization.isAdmin()).isFalse();
        assertThat(authorization.isStaff()).isFalse();
    }

    @Test
    void readsAdminAsBothAdminAndStaff() {
        signInAs(Role.ADMIN);

        assertThat(authorization.isAdmin()).isTrue();
        assertThat(authorization.isStaff()).isTrue();
    }

    @Test
    void readsStaffAsStaffButNotAdmin() {
        signInAs(Role.STAFF);

        assertThat(authorization.isStaff()).isTrue();
        assertThat(authorization.isAdmin()).isFalse();
    }

    @Test
    void readsALearnerAsNeither() {
        signInAs(Role.LEARNER);

        assertThat(authorization.isStaff()).isFalse();
        assertThat(authorization.isAdmin()).isFalse();
    }

    private void signInAs(Role role) {
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn(role);
        when(user.getId()).thenReturn(userId);
    }
}
