package com.englow3.roadmap.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RoadmapMilestoneTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");
    private final UUID paper = UUID.randomUUID();

    private RoadmapMilestone mock() {
        return RoadmapMilestone.mockExam(UUID.randomUUID(), UUID.randomUUID(), paper, new BigDecimal("6.0"));
    }

    @Test
    void passesAtTheRequiredScoreAndFailsBelowIt() {
        RoadmapMilestone milestone = mock();
        milestone.recordMockSitting(UUID.randomUUID(), paper, true, true, new BigDecimal("5.5"), NOW);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.FAILED);

        milestone.recordMockSitting(UUID.randomUUID(), paper, true, true, new BigDecimal("6.0"), NOW);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PASSED);
        assertThat(milestone.getAttemptCount()).isEqualTo(2);
        assertThat(milestone.getAchievedAt()).isEqualTo(NOW);
    }

    @Test
    void takesEachSittingOnceAndNothingAfterAPass() {
        RoadmapMilestone milestone = mock();
        UUID sitting = UUID.randomUUID();
        milestone.recordMockSitting(sitting, paper, true, true, new BigDecimal("5.0"), NOW);

        assertThatThrownBy(() -> milestone.recordMockSitting(sitting, paper, true, true, new BigDecimal("7"), NOW))
                .hasFieldOrPropertyWithValue("code", "MILESTONE_RESULT_RECORDED");

        milestone.recordMockSitting(UUID.randomUUID(), paper, true, true, new BigDecimal("7"), NOW);
        assertThatThrownBy(
                () -> milestone.recordMockSitting(UUID.randomUUID(), paper, true, true, new BigDecimal("8"), NOW))
                        .hasFieldOrPropertyWithValue("code", "MILESTONE_ALREADY_PASSED");
    }

    @Test
    void countsOnlyAFinishedFullSittingOfItsOwnPaper() {
        RoadmapMilestone milestone = mock();
        assertThatThrownBy(() -> milestone.recordMockSitting(UUID.randomUUID(), UUID.randomUUID(), true, true,
                new BigDecimal("7"), NOW)).hasFieldOrPropertyWithValue("code", "MILESTONE_WRONG_EXAM");
        assertThatThrownBy(() -> milestone.recordMockSitting(UUID.randomUUID(), paper, false, true, null, NOW))
                .hasFieldOrPropertyWithValue("code", "MILESTONE_RESULT_NOT_SCORED");
        assertThatThrownBy(
                () -> milestone.recordMockSitting(UUID.randomUUID(), paper, true, false, new BigDecimal("7"), NOW))
                        .hasFieldOrPropertyWithValue("code", "MILESTONE_RESULT_NOT_SCORED");
        assertThat(milestone.getAttemptCount()).isZero();
    }

    @Test
    void aLevelMilestoneIsNotPassedByAPaper() {
        RoadmapMilestone milestone = RoadmapMilestone.levelCheck(UUID.randomUUID(), UUID.randomUUID(), CefrLevel.B2);
        assertThatThrownBy(
                () -> milestone.recordMockSitting(UUID.randomUUID(), paper, true, true, new BigDecimal("7"), NOW))
                        .hasFieldOrPropertyWithValue("code", "MILESTONE_KIND_MISMATCH");

        milestone.recordLevelCheck(UUID.randomUUID(), CefrLevel.B1, NOW);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.FAILED);
        milestone.recordLevelCheck(UUID.randomUUID(), CefrLevel.C1, NOW);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PASSED);
    }
}
