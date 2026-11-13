package com.englow3.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.roadmap.dto.command.SetLevelCheckWeightCommand;
import com.englow3.roadmap.dto.result.LevelCheckWeightsResult;
import com.englow3.roadmap.helper.LevelCombiner.Weights;
import com.englow3.roadmap.query.LevelCheckWeightQuery;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

class LevelCheckWeightServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private LevelCheckWeightService service;
    @Autowired
    private LevelCheckWeightQuery query;

    private UUID admin;
    private Instant started;

    @BeforeEach
    void signIn() {
        admin = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, admin);
        started = Instant.now().minusSeconds(1);
    }

    /** Other tests read the seeded weights; put them back as they were. */
    @AfterEach
    void removeRowsAddedHere() {
        jdbc.sql("delete from level_check_weights where created_by_user_id = :admin").param("admin", admin).update();
        SignedIn.out();
    }

    @Test
    void listsDefaultFirstThenEveryPurposeWithWhatAppliesToIt() {
        LevelCheckWeightsResult result = service.current();

        assertThat(result.purposes().getFirst().purposeCode()).isEqualTo("DEFAULT");
        assertThat(result.purposes()).extracting(LevelCheckWeightsResult.Purpose::purposeCode).contains("TRAVEL",
                "WORK", "SCHOOL");
        assertThat(purpose(result, "TRAVEL").inForce().speaking()).isEqualTo(35);
        assertThat(purpose(result, "SCHOOL").inForce()).as("no row of its own: falls back to DEFAULT").isNull();
    }

    @Test
    void aChangeAppliesFromNowAndKeepsTheOldRowAsHistory() {
        LevelCheckWeightsResult result = service.set(new SetLevelCheckWeightCommand("SCHOOL", 50, 30, 20, 3));

        assertThat(purpose(result, "SCHOOL").inForce().vocabulary()).isEqualTo(50);
        assertThat(result.history().getFirst().purposeCode()).isEqualTo("SCHOOL");
        assertThat(query.weightsFor(List.of("SCHOOL"), Instant.now().plusSeconds(1))).contains(new Weights(50, 30, 20));
        assertThat(query.weightsFor(List.of("SCHOOL"), started)).as("before the change, DEFAULT applied")
                .contains(new Weights(40, 30, 30));
    }

    @Test
    void refusesAPurposeThatDoesNotExist() {
        assertThatThrownBy(() -> service.set(new SetLevelCheckWeightCommand("GAMING", 40, 30, 30, 0)))
                .hasFieldOrPropertyWithValue("code", "LEVEL_CHECK_PURPOSE_UNKNOWN");
    }

    private static LevelCheckWeightsResult.Purpose purpose(LevelCheckWeightsResult result, String code) {
        return result.purposes().stream().filter(p -> p.purposeCode().equals(code)).findFirst().orElseThrow();
    }
}
