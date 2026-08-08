package com.englow3.shared.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Exposes only what Supabase's JWT itself carries. The internal user id and
 * business role live in the users table, owned by the user module - resolving
 * them is that module's job, not shared's.
 */
@Component
public class CurrentUser {

    public UUID authProviderId() {
        return UUID.fromString(requireJwt().getSubject());
    }

    public String email() {
        return requireJwt().getClaimAsString("email");
    }

    private Jwt requireJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("No authenticated JWT present on the security context");
        }
        return token.getToken();
    }
}
