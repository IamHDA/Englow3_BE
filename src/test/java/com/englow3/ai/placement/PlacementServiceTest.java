package com.englow3.ai.placement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.shared.error.ConflictException;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlacementServiceTest {
    @Nested
    class Failure {

        @Test
        void refusesASecondActiveAttemptForTheSameExam() {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            UserDirectory userDirectory = mock(UserDirectory.class);
            UUID authId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();
            when(userDirectory.requireCurrentUserId()).thenReturn(userId);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(userId), eq(examId))).thenReturn(1);
            PlacementService service = new PlacementService(jdbcTemplate, userDirectory, mock(AiPromptService.class),
                    mock(AiJobService.class), new ObjectMapper());

            assertThatThrownBy(() -> service.start(examId)).isInstanceOf(ConflictException.class)
                    .hasMessageContaining("active placement attempt");
        }

    }

}
