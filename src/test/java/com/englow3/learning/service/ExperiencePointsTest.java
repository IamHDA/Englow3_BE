package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.englow3.learning.service.ExperiencePoints.Activity;
import com.englow3.learning.service.ExperiencePoints.Level;

class ExperiencePointsTest {

    @Test
    void awardsNothingForNoActivity() {
        assertThat(ExperiencePoints.totalXp(Activity.NONE)).isZero();
    }

    @Test
    void weighsEachKindOfWorkSeparately() {
        long total = ExperiencePoints.totalXp(new Activity(10, 4, 2, 1));

        assertThat(total)
                .isEqualTo(10 * ExperiencePoints.FLASHCARD_REVIEW_XP + 4 * ExperiencePoints.DICTATION_SENTENCE_XP
                        + 2 * ExperiencePoints.QUIZ_ATTEMPT_XP + ExperiencePoints.EXAM_ATTEMPT_XP);
    }

    /** A learner who has done nothing is level 1, not level 0 - there is no level 0 to be on. */
    @Test
    void startsEveryoneAtLevelOne() {
        Level level = ExperiencePoints.levelFor(0);

        assertThat(level.level()).isEqualTo(1);
        assertThat(level.xpIntoLevel()).isZero();
        assertThat(level.levelCostXp()).isEqualTo(ExperiencePoints.FIRST_LEVEL_COST_XP);
    }

    @Test
    void keepsTheRemainderWhenPartWayThroughALevel() {
        Level level = ExperiencePoints.levelFor(ExperiencePoints.FIRST_LEVEL_COST_XP - 1);

        assertThat(level.level()).isEqualTo(1);
        assertThat(level.xpIntoLevel()).isEqualTo(ExperiencePoints.FIRST_LEVEL_COST_XP - 1);
    }

    /** Exactly enough is enough: the boundary belongs to the new level, not the old one. */
    @Test
    void promotesOnTheExactThreshold() {
        Level level = ExperiencePoints.levelFor(ExperiencePoints.FIRST_LEVEL_COST_XP);

        assertThat(level.level()).isEqualTo(2);
        assertThat(level.xpIntoLevel()).isZero();
    }

    @Test
    void makesEachLevelCostMoreThanTheOneBefore() {
        long firstTwo = ExperiencePoints.FIRST_LEVEL_COST_XP
                + (ExperiencePoints.FIRST_LEVEL_COST_XP + ExperiencePoints.LEVEL_COST_GROWTH_XP);

        Level justBefore = ExperiencePoints.levelFor(firstTwo - 1);
        Level atThird = ExperiencePoints.levelFor(firstTwo);

        assertThat(justBefore.level()).isEqualTo(2);
        assertThat(atThird.level()).isEqualTo(3);
        assertThat(atThird.levelCostXp())
                .isEqualTo(ExperiencePoints.FIRST_LEVEL_COST_XP + 2L * ExperiencePoints.LEVEL_COST_GROWTH_XP);
    }

    /** Nothing writes a negative count, but a level of -3 would be a worse answer than clamping. */
    @Test
    void treatsANegativeTotalAsNothing() {
        assertThat(ExperiencePoints.levelFor(-50)).isEqualTo(new Level(1, 0, 0, ExperiencePoints.FIRST_LEVEL_COST_XP));
    }
}
