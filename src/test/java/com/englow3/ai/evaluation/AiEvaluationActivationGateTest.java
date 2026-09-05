package com.englow3.ai.evaluation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.shared.error.ConflictException;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiEvaluationActivationGateTest {
    private AiEvaluationService service(JdbcTemplate jdbc) {
        return new AiEvaluationService(jdbc, new ObjectMapper(), mock(UserDirectory.class));
    }

    @Nested
    class Success {

        @Test
        void permitsModelActivationOnlyForAnAcceptedMatchingRun() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            UUID runId = UUID.randomUUID();
            when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(runId), eq("TUTOR"), eq("ai-service"),
                    eq("candidate-model"))).thenReturn(1);
            AiEvaluationService service = service(jdbc);

            assertThatCode(() -> service.requireAcceptedForModel("TUTOR", "ai-service", "candidate-model", runId))
                    .doesNotThrowAnyException();
        }

    }

    @Nested
    class Failure {

        @Test
        void blocksModelActivationWithoutAnAcceptedMatchingRun() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            UUID runId = UUID.randomUUID();
            when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(runId), eq("TUTOR"), eq("ai-service"),
                    eq("candidate-model"))).thenReturn(0);
            AiEvaluationService service = service(jdbc);

            assertThatThrownBy(() -> service.requireAcceptedForModel("TUTOR", "ai-service", "candidate-model", runId))
                    .isInstanceOf(ConflictException.class);
        }

    }

}
