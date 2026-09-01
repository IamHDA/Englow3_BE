package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.NotFoundException;
import com.englow3.user.dto.result.UserIdentityResult;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

class UserDirectoryTest {

    private final UserRepository userRepo = mock(UserRepository.class);

    private final UserDirectory directory = new UserDirectory(userRepo);

    private final UUID authProviderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);

    @Test
    void resolvesTheInternalIdBehindAnAuthProviderId() {
        givenAUserWithRole(Role.LEARNER);

        UserIdentityResult identity = directory.resolve(authProviderId);

        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.isAdmin()).isFalse();
    }

    @Test
    void reportsAnAdminAsOne() {
        givenAUserWithRole(Role.ADMIN);

        assertThat(directory.resolve(authProviderId).isAdmin()).isTrue();
    }

    @Test
    void failsWhenTheAuthProviderIdPointsAtNoUserRow() {
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directory.resolve(authProviderId)).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND");
    }

    private void givenAUserWithRole(Role role) {
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(role);
    }
}
