package com.englow3.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.roadmap.helper.LevelCombiner.Weights;
import com.englow3.roadmap.query.LevelCheckWeightQuery;
import com.englow3.shared.persistence.SqlTime;
import com.englow3.support.PostgresIntegrationTest;

class LevelCheckWeightQueryIntegrationTest extends PostgresIntegrationTest {

    /** Far enough ahead that rows added here never apply to another test, which asks about now. */
    private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private LevelCheckWeightQuery query;

    @AfterEach
    void removeFutureRows() {
        jdbc.sql("delete from level_check_weights where effective_from >= :from").param("from", SqlTime.at(FUTURE))
                .update();
    }

    @Test
    void readsTheSeededDefaultsByPurpose() {
        Instant now = Instant.now();
        assertThat(query.weightsFor(List.of("TRAVEL"), now)).contains(new Weights(35, 30, 35));
        assertThat(query.weightsFor(List.of("WORK"), now)).contains(new Weights(40, 30, 30));
        assertThat(query.weightsFor(List.of("SCHOOL"), now)).as("no row of its own: DEFAULT")
                .contains(new Weights(40, 30, 30));
        assertThat(query.weightsFor(List.of(), now)).contains(new Weights(40, 30, 30));
    }

    @Test
    void theHighestPriorityPurposeWins() {
        assertThat(query.weightsFor(List.of("WORK", "COMMUNICATION"), Instant.now())).contains(new Weights(35, 30, 35));
    }

    /** Tuning is a new row, not an edit: it applies from its date and leaves earlier results as they were. */
    @Test
    void aNewerRowTakesOverFromItsDateOnly() {
        jdbc.sql("""
                insert into level_check_weights (id, purpose_code, vocabulary, listening, speaking, priority,
                                                 effective_from)
                values (:id, 'WORK', 30, 30, 40, 5, :from)
                """).param("id", UUID.randomUUID()).param("from", SqlTime.at(FUTURE)).update();

        assertThat(query.weightsFor(List.of("WORK"), FUTURE.minusSeconds(1))).contains(new Weights(40, 30, 30));
        assertThat(query.weightsFor(List.of("WORK"), FUTURE.plusSeconds(1))).contains(new Weights(30, 30, 40));
    }
}
