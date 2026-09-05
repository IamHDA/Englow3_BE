package com.englow3.ai.foundation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AiJobEventStreamTest {
    @Nested
    class Success {

        @SuppressWarnings("unchecked")
        @Test
        void replayQueryIsAlwaysScopedToTheAuthenticatedOwnerAndCursor() {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            UUID userId = UUID.randomUUID();
            when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());
            AiJobEventStream stream = new AiJobEventStream(jdbcTemplate);

            stream.subscribe(userId, 42);

            verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), eq(userId), eq(42L), eq(1_001));
        }

    }

}
