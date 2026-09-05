package com.englow3.ai.learningpath;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class LearningPathServiceTest {
    @Nested
    class Failure {

        @SuppressWarnings("unchecked")
        @Test
        void reportsWhenCurrentUserHasNoActivePath() throws Exception {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            UserDirectory userDirectory = mock(UserDirectory.class);
            UUID authId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(userDirectory.requireCurrentUserId()).thenReturn(userId);
            when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                    .thenAnswer(call -> {
                        ResultSetExtractor<UUID> extractor = call.getArgument(1);
                        ResultSet resultSet = mock(ResultSet.class);
                        when(resultSet.next()).thenReturn(false);
                        return extractor.extractData(resultSet);
                    });
            LearningPathService service = new LearningPathService(jdbcTemplate, userDirectory,
                    mock(LearnerProfileRepository.class), mock(AiPromptService.class), mock(AiJobService.class),
                    new ObjectMapper(), mock(LearningContentResolver.class), new BktMasteryCalculator());

            assertThatThrownBy(service::current).isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("No active learning path");
        }

    }

}
