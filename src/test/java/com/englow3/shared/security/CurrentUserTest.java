package com.englow3.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserTest {
    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID authProviderId, String email) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(authProviderId.toString())
                .claim("email", email).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Nested
    class Success {

        @Test
        void readsSubjectAndEmailFromTheAuthenticatedJwt() {
            UUID authProviderId = UUID.randomUUID();
            authenticateAs(authProviderId, "learner@example.com");

            assertThat(currentUser.authProviderId()).isEqualTo(authProviderId);
            assertThat(currentUser.email()).isEqualTo("learner@example.com");
        }

    }

    @Nested
    class Failure {

        @Test
        void rejectsAccessWhenNoJwtIsPresentOnTheContext() {
            assertThatThrownBy(currentUser::authProviderId).isInstanceOf(IllegalStateException.class);
        }

    }

}
