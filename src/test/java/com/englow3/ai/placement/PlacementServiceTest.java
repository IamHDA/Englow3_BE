package com.englow3.ai.placement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlacementServiceTest {

    @Test
    void refusesASecondActiveAttemptForTheSameExam() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = mock(User.class);
        UUID authId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authId);
        when(userRepository.findByAuthProviderId(authId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(userId), eq(examId))).thenReturn(1);
        PlacementService service = new PlacementService(jdbcTemplate, userRepository, currentUser,
                mock(AiPromptService.class), mock(AiJobService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.start(examId)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("active placement attempt");
    }
}
