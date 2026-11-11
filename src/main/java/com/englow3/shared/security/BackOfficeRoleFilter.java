package com.englow3.shared.security;

import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.englow3.user.api.UserDirectory;
import com.englow3.shared.error.ApiErrorResponse;
import com.englow3.shared.logging.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** One indexed role lookup for privileged HTTP calls; learner requests retain local JWT verification. */
public final class BackOfficeRoleFilter extends OncePerRequestFilter {
    private final UserDirectory users;
    private final ObjectMapper mapper;

    public BackOfficeRoleFilter(UserDirectory users, ObjectMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/api/admin") || path.startsWith("/api/admin/") || path.equals("/actuator/startup")
                || path.equals("/actuator/metrics") || path.startsWith("/actuator/metrics/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token && token.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()))) {
            String role = users.currentRole();
            boolean matches = ("ADMIN".equals(role) || "STAFF".equals(role))
                    && token.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
            if (!matches) {
                response.setStatus(403);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                mapper.writeValue(response.getWriter(), ApiErrorResponse.of("ROLE_CHANGED",
                        "Your access changed; sign in again", TraceIdFilter.current()));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
