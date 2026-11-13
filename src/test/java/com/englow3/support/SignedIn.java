package com.englow3.support;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Signs a fixture learner in, the way a real request would be.
 * <p>
 * A token whose subject is the learner's {@code auth_provider_id} goes into the security context, so the service under
 * test resolves its caller through the real {@code UserDirectory} and the real {@code users} table. The alternative -
 * swapping a mocked directory into the service bean - changes a singleton the whole suite shares, and outlives the test
 * that did it.
 */
public final class SignedIn {

    private SignedIn() {
    }

    public static void as(JdbcClient jdbc, UUID userId) {
        as(jdbc, userId, new String[0]);
    }

    /**
     * With roles granted as the app's converter grants them ({@code ROLE_ADMIN}), for calling a controller whose
     * {@code @PreAuthorize} would otherwise refuse.
     */
    public static void as(JdbcClient jdbc, UUID userId, String... roles) {
        UUID authProviderId = jdbc.sql("select auth_provider_id from users where id = :id").param("id", userId)
                .query(UUID.class).single();

        Jwt jwt = Jwt.withTokenValue("integration-test").header("alg", "none").subject(authProviderId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, java.util.Arrays.stream(roles).map(
                        role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                        .toList()));
    }

    public static void out() {
        SecurityContextHolder.clearContext();
    }
}
