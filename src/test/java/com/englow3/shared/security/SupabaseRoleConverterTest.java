package com.englow3.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseRoleConverterTest {

    private final SupabaseRoleConverter converter = new SupabaseRoleConverter();

    @Test
    void readsTheRoleOutOfAppMetadata() {
        assertThat(converter.convert(tokenWith(Map.of("role", "ADMIN")))).extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void upperCasesTheRoleSoHasRoleMatchesWhateverSupabaseWasGiven() {
        assertThat(converter.convert(tokenWith(Map.of("role", " staff ")))).extracting(Object::toString)
                .containsExactly("ROLE_STAFF");
    }

    /** No claim means no authority, so every role check refuses - the gate must never fall open. */
    @Test
    void grantsNothingWhenAppMetadataIsAbsent() {
        assertThat(converter.convert(tokenWith(null))).isEmpty();
    }

    @Test
    void grantsNothingWhenTheRoleKeyIsMissingOrBlank() {
        assertThat(converter.convert(tokenWith(Map.of("provider", "email")))).isEmpty();
        assertThat(converter.convert(tokenWith(Map.of("role", "  ")))).isEmpty();
    }

    /** A role that is not a string is malformed data, not an authority. */
    @Test
    void grantsNothingWhenTheRoleIsNotAString() {
        assertThat(converter.convert(tokenWith(Map.of("role", 7)))).isEmpty();
    }

    private static Jwt tokenWith(Map<String, Object> appMetadata) {
        Jwt.Builder token = Jwt.withTokenValue("token").header("alg", "ES256").subject("a-supabase-user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
        if (appMetadata != null) {
            token.claim("app_metadata", appMetadata);
        }
        return token.build();
    }
}
