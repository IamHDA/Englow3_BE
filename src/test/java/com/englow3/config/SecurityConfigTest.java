package com.englow3.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityProbeController.class, properties = {
        "SUPABASE_ISSUER_URI=https://issuer.example.test/auth/v1",
        "SUPABASE_JWKS_URI=https://issuer.example.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Test
    void diagnosticsRequireAuthentication() throws Exception {
        for (String path : new String[] { "/actuator/startup", "/actuator/metrics",
                "/actuator/metrics/hikaricp.connections.acquire" })
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "LEARNER", "STAFF" })
    void onlyAdminsCanReadStartupAndPoolDiagnostics(String role) throws Exception {
        for (String path : new String[] { "/actuator/startup", "/actuator/metrics",
                "/actuator/metrics/hikaricp.connections.acquire" })
            mockMvc.perform(get(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .user("test").roles(role)))
                    .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    void adminCanReadStartupAndPoolDiagnostics() throws Exception {
        for (String path : new String[] { "/actuator/startup", "/actuator/metrics",
                "/actuator/metrics/hikaricp.connections.acquire" })
            mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    class Success {

        @Test
        void exposesHealthProbePathsWithoutAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        }

        /**
         * The pricing page is public marketing; it is the one /api route a visitor may read. This slice has no handler
         * for it, so "let through" shows as 404 where "turned away" would be 401.
         */
        @Test
        void letsAVisitorReachThePlanCatalogue() throws Exception {
            mockMvc.perform(get("/api/plans")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Failure {

        @Test
        void keepsApplicationEndpointsAuthenticated() throws Exception {
            mockMvc.perform(get("/api/private-test")).andExpect(status().isUnauthorized());
        }

        @Test
        void keepsAccountAndSubscriptionEndpointsAuthenticated() throws Exception {
            mockMvc.perform(get("/api/user/me/export")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/user/me/subscription")).andExpect(status().isUnauthorized());
            mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/user/me/erase"))
                    .andExpect(status().isUnauthorized());
        }

        /** Granting a plan is an administrator's act; staff and learners are turned away at the filter. */
        @Test
        void keepsPlanGrantsOutOfReachOfLearners() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/admin/subscriptions")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .user("test").roles("LEARNER")))
                    .andExpect(status().isForbidden());
        }
    }
}

@RestController
class SecurityProbeController {
    @GetMapping({ "/actuator/startup", "/actuator/metrics", "/actuator/metrics/hikaricp.connections.acquire" })
    String diagnostics() {
        return "diagnostics";
    }

    @GetMapping({ "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness" })
    String health() {
        return "up";
    }

    @GetMapping("/api/private-test")
    String privateEndpoint() {
        return "private";
    }
}
