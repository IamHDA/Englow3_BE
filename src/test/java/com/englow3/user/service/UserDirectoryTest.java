package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

class UserDirectoryTest {
    private final UserRepository userRepo = mock(UserRepository.class);

    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final UserDirectory userDirectory = new UserDirectory(userRepo, currentUser);

    private final UUID authProviderId = UUID.randomUUID();

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        when(currentUser.authProviderId()).thenReturn(authProviderId);
    }

    @Nested
    class Success {

        @Test
        void resolvesTheInternalIdBehindTheToken() {
            User user = mock(User.class);
            when(user.getId()).thenReturn(userId);
            when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));

            assertThat(userDirectory.requireCurrentUserId()).isEqualTo(userId);
        }

    }

    @Nested
    class Failure {

        /** No row behind a valid token is a sync defect, and worth surfacing rather than hiding behind a refusal. */
        @Test
        void failsWhenTheTokenPointsAtNoUserRow() {
            when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.empty());

            assertThatThrownBy(userDirectory::requireCurrentUserId).isInstanceOf(NotFoundException.class)
                    .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND");
        }

    }

}
