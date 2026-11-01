package com.englow3.roadmap.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class LearningRoadmapTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private static LearningRoadmap generating(UUID token) {
        return LearningRoadmap.forLevel(UUID.randomUUID(), UUID.randomUUID(), CefrLevel.B2,
                LocalDate.parse("2027-06-01"), CefrLevel.A2, 30, token);
    }

    private static LearningRoadmap active() {
        UUID token = UUID.randomUUID();
        LearningRoadmap roadmap = generating(token);
        roadmap.activate(token, "model", "v1");
        return roadmap;
    }

    @Test
    void aLevelGoalMustBeAboveWhereTheLearnerStarts() {
        assertThatThrownBy(() -> LearningRoadmap.forLevel(UUID.randomUUID(), UUID.randomUUID(), CefrLevel.B1, null,
                CefrLevel.B1, 30, UUID.randomUUID())).isInstanceOf(BadRequestException.class)
                        .hasFieldOrPropertyWithValue("code", "ROADMAP_TARGET_NOT_ABOVE_START");
    }

    @Test
    void cannotPlanWithoutAKnownStartingLevel() {
        assertThatThrownBy(() -> LearningRoadmap.forCertificate(UUID.randomUUID(), UUID.randomUUID(), "IELTS",
                new BigDecimal("6.5"), null, null, null, 30, UUID.randomUUID())).hasFieldOrPropertyWithValue("code",
                        "ROADMAP_LEVEL_UNKNOWN");
    }

    @Test
    void onlyTheResultOfTheCurrentRequestActivatesThePlan() {
        UUID token = UUID.randomUUID();
        LearningRoadmap roadmap = generating(token);

        assertThatThrownBy(() -> roadmap.activate(UUID.randomUUID(), "m", "v1")).hasFieldOrPropertyWithValue("code",
                "ROADMAP_RESULT_STALE");

        roadmap.activate(token, "m", "v1");
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.ACTIVE);
        assertThat(roadmap.getPendingJobToken()).isNull();
    }

    @Test
    void aFailedFirstPlanFailsTheRoadmapButAFailedAdjustmentKeepsThePlan() {
        UUID token = UUID.randomUUID();
        LearningRoadmap first = generating(token);
        assertThat(first.giveUp(UUID.randomUUID(), "X")).isFalse();
        assertThat(first.giveUp(token, "LLM_DOWN")).isTrue();
        assertThat(first.getStatus()).isEqualTo(RoadmapStatus.FAILED);

        LearningRoadmap roadmap = active();
        UUID adjust = UUID.randomUUID();
        roadmap.requestAdjustment(AdjustmentReason.MILESTONE_FAILED, adjust);
        roadmap.giveUp(adjust, "LLM_DOWN");
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.ACTIVE);
        assertThat(roadmap.isAdjusting()).isFalse();
        assertThat(roadmap.getFailureCode()).isEqualTo("LLM_DOWN");
    }

    @Test
    void oneAdjustmentAtATimeAndOnlyOnAnActivePlan() {
        LearningRoadmap roadmap = active();
        roadmap.requestAdjustment(AdjustmentReason.BEHIND_SCHEDULE, UUID.randomUUID());

        assertThatThrownBy(() -> roadmap.requestAdjustment(AdjustmentReason.GOAL_CHANGED, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "ROADMAP_ADJUSTMENT_IN_PROGRESS");
        assertThatThrownBy(
                () -> generating(UUID.randomUUID()).requestAdjustment(AdjustmentReason.GOAL_CHANGED, UUID.randomUUID()))
                        .hasFieldOrPropertyWithValue("code", "ROADMAP_NOT_ADJUSTABLE");
    }

    @Test
    void cancellingAnAdjustmentMakesItsLateResultStale() {
        LearningRoadmap roadmap = active();
        UUID adjust = UUID.randomUUID();
        roadmap.requestAdjustment(AdjustmentReason.BEHIND_SCHEDULE, adjust);

        roadmap.cancelAdjustment();

        assertThat(roadmap.isWaitingFor(adjust)).isFalse();
        assertThatThrownBy(() -> roadmap.applyAdjustment(adjust, "m", "v1")).hasFieldOrPropertyWithValue("code",
                "ROADMAP_RESULT_STALE");
    }

    @Test
    void completesOnlyWhenTheLastMilestoneIsPassed() {
        LearningRoadmap roadmap = active();
        RoadmapMilestone milestone = RoadmapMilestone.levelCheck(UUID.randomUUID(), UUID.randomUUID(), CefrLevel.B2);

        assertThatThrownBy(() -> roadmap.complete(milestone, NOW)).hasFieldOrPropertyWithValue("code",
                "ROADMAP_FINAL_MILESTONE_PENDING");

        milestone.recordLevelCheck(UUID.randomUUID(), CefrLevel.B2, NOW);
        roadmap.complete(milestone, NOW);
        assertThat(roadmap.getStatus()).isEqualTo(RoadmapStatus.COMPLETED);
    }

    @Test
    void aFinishedRoadmapNeverChangesAgain() {
        LearningRoadmap roadmap = active();
        roadmap.abandon(NOW);

        assertThatThrownBy(() -> roadmap.supersede(NOW)).hasFieldOrPropertyWithValue("code", "ROADMAP_CLOSED");
        assertThatThrownBy(() -> roadmap.changeGoal(null, CefrLevel.C1, null, 30)).hasFieldOrPropertyWithValue("code",
                "ROADMAP_CLOSED");
    }
}
