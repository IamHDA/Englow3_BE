package com.englow3.shared.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public UUID authProviderId() {
        return UUID.fromString(requireJwt().getSubject());
    }

    public String email() {
        return requireJwt().getClaimAsString("email");
    }

    /**
     * Whether the caller holds a role, for the rare rule that depends on who is asking rather than only on whether they
     * may ask at all - that second question stays with {@code @PreAuthorize}. False when nobody is signed in.
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    private Jwt requireJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("No authenticated JWT present on the security context");
        }
        return token.getToken();
    }
}
