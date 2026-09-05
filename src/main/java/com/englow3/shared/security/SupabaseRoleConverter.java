package com.englow3.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns the Supabase {@code app_metadata.role} claim into the authority {@code hasRole(...)} looks for.
 * {@code app_metadata} and not {@code user_metadata}: the second is writable by the user through
 * {@code auth.updateUser()}, so trusting it would let any learner make themselves an admin. Only {@code service_role}
 * can write {@code app_metadata}, which is what makes it safe to authorise from. A missing or malformed claim yields
 * **no authority**, so the endpoint refuses rather than falling open. A token minted before the role was set simply has
 * none until it is refreshed - that lag is the price of reading the role from the token instead of the database. This
 * class knows no {@code Role} type: the enum belongs to {@code user}, which owns the column, and {@code shared} must
 * not depend on a module.
 */
public class SupabaseRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String APP_METADATA = "app_metadata";
    private static final String ROLE = "role";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> appMetadata = jwt.getClaimAsMap(APP_METADATA);
        if (appMetadata == null || !(appMetadata.get(ROLE) instanceof String role) || role.isBlank()) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase(Locale.ROOT)));
    }
}
