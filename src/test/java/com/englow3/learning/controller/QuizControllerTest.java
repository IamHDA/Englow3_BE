package com.englow3.learning.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.englow3.config.SecurityConfig;
import com.englow3.learning.dto.result.QuizAttemptResult;
import com.englow3.learning.entity.Quiz;
import com.englow3.learning.entity.QuizAttempt;
import com.englow3.learning.service.QuizService;

/**
 * Starting a quiz twice at once. Both requests find nothing open and both insert; the partial unique index lets one
 * through and the other used to answer 409 DATA_CONFLICT. It now asks again and is handed the attempt that won.
 */
@WebMvcTest(controllers = QuizController.class, properties = {
        "SUPABASE_ISSUER_URI=https://issuer.example.test/auth/v1",
        "SUPABASE_JWKS_URI=https://issuer.example.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class QuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private QuizService quizService;

    @Test
    void handsTheLoserOfADoubleStartTheAttemptThatWon() throws Exception {
        Quiz quiz = Quiz.draft("tenses", "Tenses", "", "Grammar", "B1", 600, (short) 60, UUID.randomUUID());
        quiz.publish(1, 1, Instant.now());
        QuizAttempt winner = QuizAttempt.start(quiz, UUID.randomUUID(), 1, BigDecimal.ONE, Instant.now());
        when(quizService.start(quiz.getId()))
                .thenThrow(new DataIntegrityViolationException("uq_quiz_attempts_one_active"))
                .thenReturn(QuizAttemptResult.started(winner, quiz.getTitle(), true));

        mockMvc.perform(post("/api/quizzes/{id}/attempts", quiz.getId())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LEARNER")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(winner.getId().toString()));

        verify(quizService, times(2)).start(quiz.getId());
    }
}
