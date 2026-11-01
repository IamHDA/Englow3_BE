package com.englow3.roadmap.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RoadmapStageTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private static RoadmapStage current() {
        return RoadmapStage.plan(UUID.randomUUID(), UUID.randomUUID(), 1, "Nền tảng", "", CefrLevel.A2, CefrLevel.B1,
                "LISTENING", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-12-13"), true);
    }

    @Test
    void refusesAStageThatGoesDownOrEndsBeforeItStarts() {
        assertThatThrownBy(() -> RoadmapStage.plan(UUID.randomUUID(), UUID.randomUUID(), 1, "", "", CefrLevel.B1,
                CefrLevel.A2, "", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-12-01"), true))
                        .hasFieldOrPropertyWithValue("code", "ROADMAP_STAGE_INVALID");
        assertThatThrownBy(() -> RoadmapStage.plan(UUID.randomUUID(), UUID.randomUUID(), 1, "", "", CefrLevel.A2,
                CefrLevel.B1, "", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-01"), true))
                        .hasFieldOrPropertyWithValue("code", "ROADMAP_STAGE_INVALID");
    }

    @Test
    void countsAPartWeekAsAWeek() {
        assertThat(current().weeks()).isEqualTo(6);
    }

    @Test
    void isPassedOnlyByItsOwnPassedMilestone() {
        RoadmapStage stage = current();
        RoadmapMilestone other = RoadmapMilestone.levelCheck(UUID.randomUUID(), UUID.randomUUID(), CefrLevel.B1);
        other.recordLevelCheck(UUID.randomUUID(), CefrLevel.B1, NOW);
        RoadmapMilestone own = RoadmapMilestone.levelCheck(UUID.randomUUID(), stage.getId(), CefrLevel.B1);

        assertThatThrownBy(() -> stage.pass(other)).hasFieldOrPropertyWithValue("code", "ROADMAP_MILESTONE_NOT_PASSED");
        assertThatThrownBy(() -> stage.pass(own)).hasFieldOrPropertyWithValue("code", "ROADMAP_MILESTONE_NOT_PASSED");

        own.recordLevelCheck(UUID.randomUUID(), CefrLevel.B2, NOW);
        stage.pass(own);
        assertThat(stage.getStatus()).isEqualTo(RoadmapStageStatus.PASSED);
    }

    @Test
    void aPassedStageIsNeverReplaced() {
        RoadmapStage stage = current();
        RoadmapMilestone own = RoadmapMilestone.levelCheck(UUID.randomUUID(), stage.getId(), CefrLevel.B1);
        own.recordLevelCheck(UUID.randomUUID(), CefrLevel.B1, NOW);
        stage.pass(own);

        assertThatThrownBy(stage::replace).hasFieldOrPropertyWithValue("code", "ROADMAP_STAGE_PASSED");

        RoadmapStage later = current();
        later.replace();
        assertThat(later.isInForce()).isFalse();
    }
}
