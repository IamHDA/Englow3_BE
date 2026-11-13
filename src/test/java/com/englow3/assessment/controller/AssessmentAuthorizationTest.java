package com.englow3.assessment.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.englow3.config.SecurityConfig;
import com.englow3.assessment.service.AssessmentAuthoringService;

@WebMvcTest(controllers = AssessmentAuthoringController.class, properties = {
        "SUPABASE_ISSUER_URI=https://issuer.test/auth/v1",
        "SUPABASE_JWKS_URI=https://issuer.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class AssessmentAuthorizationTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    AssessmentAuthoringService service;

    @Test
    void guestCannotListAuthoring() throws Exception {
        mvc.perform(get("/api/admin/assessments/tasks")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void learnerCannotListAuthoring() throws Exception {
        mvc.perform(
                get("/api/admin/assessments/tasks").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LEARNER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void staffCannotApprove() throws Exception {
        mvc.perform(post("/api/admin/assessments/tasks/" + UUID.randomUUID() + "/approve")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void staffCannotReject() throws Exception {
        mvc.perform(post("/api/admin/assessments/tasks/" + UUID.randomUUID() + "/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"change\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void validatesEmptyFeedback() throws Exception {
        mvc.perform(post("/api/admin/assessments/submissions/" + UUID.randomUUID() + "/grade")
                .contentType(MediaType.APPLICATION_JSON).content("{\"report\":\"{}\",\"note\":\"\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
