package com.englow3.ai.learningpath;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class LearningPathServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void reportsWhenCurrentUserHasNoActivePath() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = mock(User.class);
        UUID authId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authId);
        when(userRepository.findByAuthProviderId(authId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class))).thenAnswer(call -> {
            ResultSetExtractor<UUID> extractor = call.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(false);
            return extractor.extractData(resultSet);
        });
        LearningPathService service = new LearningPathService(jdbcTemplate, userRepository,
                mock(LearnerProfileRepository.class), currentUser, mock(AiPromptService.class),
                mock(AiJobService.class), new ObjectMapper());

        assertThatThrownBy(service::current).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No active learning path");
    }
}
