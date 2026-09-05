package com.englow3.ai.writing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class WritingAssessmentServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final UserDirectory userDirectory = mock(UserDirectory.class);

    private final AiPromptService promptService = mock(AiPromptService.class);

    private final AiJobService jobService = mock(AiJobService.class);

    private final UUID userId = UUID.randomUUID();

    private WritingAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new WritingAssessmentService(jdbcTemplate, userDirectory, promptService, jobService,
                new ObjectMapper());
    }

    @Nested
    class Failure {

        @Test
        void rejectsWhitespaceOnlyResponseBeforeAnyDatabaseWrite() {
            when(userDirectory.requireCurrentUserId()).thenReturn(userId);

            assertThatThrownBy(() -> service
                    .submit(new WritingDtos.CreateSubmissionRequest("task-1", "   \n\t ", "writing-empty")))
                            .isInstanceOf(BadRequestException.class).hasMessage("Writing response is required");
            verifyNoInteractions(jdbcTemplate, promptService, jobService);
        }

        @Test
        void rejectsResponsesAboveTheHardWordLimitBeforeAnyDatabaseWrite() {
            when(userDirectory.requireCurrentUserId()).thenReturn(userId);
            String oversized = IntStream.range(0, 2001).mapToObj(index -> "word").collect(Collectors.joining(" "));

            assertThatThrownBy(() -> service
                    .submit(new WritingDtos.CreateSubmissionRequest("task-1", oversized, "writing-too-long")))
                            .isInstanceOf(BadRequestException.class).hasMessageContaining("2000 words");
            verifyNoInteractions(jdbcTemplate, promptService, jobService);
        }

        @Test
        void rejectsSubmissionWhenJwtHasNoLinkedInternalUser() {
            when(userDirectory.requireCurrentUserId())
                    .thenThrow(new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));

            assertThatThrownBy(() -> service
                    .submit(new WritingDtos.CreateSubmissionRequest("task-1", "A valid response.", "writing-no-user")))
                            .isInstanceOf(NotFoundException.class).hasMessageContaining("No internal user");
            verifyNoInteractions(jdbcTemplate, promptService, jobService);
        }

    }

}
