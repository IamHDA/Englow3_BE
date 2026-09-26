package com.englow3.quiz.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.englow3.config.SecurityConfig;
import com.englow3.quiz.dto.result.ContentReviewResult;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.service.AdminQuizService;

/**
 * The quiz authoring list, at the HTTP layer.
 * <p>
 * The admin content screen asks for each kind of content at {@code GET /api/admin/{kind}}. Flashcards, dictation and
 * speaking all answered; quizzes did not - the endpoint had gone, leaving its imports behind, and the screen's quiz tab
 * failed with "method not allowed" for every administrator. A service test could not have noticed: the service was
 * fine, there was simply nothing routing to it.
 */
@WebMvcTest(controllers = AdminQuizController.class, properties = {
        "SUPABASE_ISSUER_URI=https://issuer.example.test/auth/v1",
        "SUPABASE_JWKS_URI=https://issuer.example.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class AdminQuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminQuizService adminQuizService;

    @Test
    void listsQuizzesForAuthoring() throws Exception {
        var draft = new ContentReviewResult(UUID.randomUUID(), "tenses", "Tenses", "DRAFT", 12,
                Instant.parse("2026-09-01T00:00:00Z"), null, null, null, null, null);
        when(adminQuizService.searchForAuthoring(any(), any(), any())).thenReturn(new PageImpl<>(List.of(draft)));

        mockMvc.perform(get("/api/admin/quizzes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Tenses"))
                .andExpect(jsonPath("$.items[0].itemCount").value(12));
    }

    /** The same endpoint is the review queue: a status narrows it, and no status means every status. */
    @Test
    void passesTheStatusFilterThrough() throws Exception {
        when(adminQuizService.searchForAuthoring(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/quizzes").param("status", "PENDING_REVIEW")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk());

        verify(adminQuizService).searchForAuthoring(eq(QuizStatus.PENDING_REVIEW), any(), any());
    }

    @Test
    void refusesALearner() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LEARNER"))))
                .andExpect(status().isForbidden());
    }
}
