package com.englow3.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void exposesHealthProbePathsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void keepsApplicationEndpointsAuthenticated() throws Exception {
        mockMvc.perform(get("/api/private-test")).andExpect(status().isUnauthorized());
    }
}

@RestController
class SecurityProbeController {

    @GetMapping({ "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness" })
    String health() {
        return "up";
    }

    @GetMapping("/api/private-test")
    String privateEndpoint() {
        return "private";
    }
}
