package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.englow3.learning.entity.DailyQuestKind;
import com.englow3.learning.service.DailyQuests.Quest;
import com.englow3.learning.service.DailyQuests.TodayActivity;

class DailyQuestsTest {

    private static final TodayActivity IDLE = new TodayActivity(0, 0, 0, 0, 0);

    @Test
    void offersTheFixedGoalsToALearnerWhoHasDoneNothingToday() {
        List<Quest> quests = DailyQuests.forToday(IDLE);

        assertThat(quests).extracting(Quest::kind).containsExactly(DailyQuestKind.PASS_A_QUIZ,
                DailyQuestKind.TYPE_SENTENCES, DailyQuestKind.PRACTISE_EVERY_DAY);
        assertThat(quests).allSatisfy(quest -> assertThat(quest.progress()).isZero());
    }

    /**
     * No cards in play means no card goal. A goal of "review 0 of 0 cards", already complete the moment it appears, is
     * clutter that teaches the learner to ignore the panel.
     */
    @Test
    void hidesTheReviewGoalWhenNothingIsDueAndNothingWasDone() {
        assertThat(DailyQuests.forToday(IDLE)).extracting(Quest::kind).doesNotContain(DailyQuestKind.REVIEW_DUE_CARDS);
    }

    @Test
    void setsTheReviewTargetToWhatWasDueAcrossTheWholeDay() {
        List<Quest> quests = DailyQuests.forToday(new TodayActivity(8, 12, 0, 0, 1));

        assertThat(quests).filteredOn(quest -> quest.kind() == DailyQuestKind.REVIEW_DUE_CARDS).singleElement()
                .satisfies(quest -> {
                    assertThat(quest.progress()).isEqualTo(8);
                    assertThat(quest.target()).isEqualTo(20);
                    assertThat(quest.completed()).isFalse();
                });
    }

    /** Clearing the last due card finishes the goal, because nothing is left to be due. */
    @Test
    void completesTheReviewGoalWhenNothingIsDueAnyMore() {
        List<Quest> quests = DailyQuests.forToday(new TodayActivity(20, 0, 0, 0, 1));

        assertThat(quests).filteredOn(quest -> quest.kind() == DailyQuestKind.REVIEW_DUE_CARDS).singleElement()
                .satisfies(quest -> assertThat(quest.completed()).isTrue());
    }

    @Test
    void countsOnePassRatherThanEveryPass() {
        List<Quest> quests = DailyQuests.forToday(new TodayActivity(0, 0, 4, 0, 1));

        assertThat(quests).filteredOn(quest -> quest.kind() == DailyQuestKind.PASS_A_QUIZ).singleElement()
                .satisfies(quest -> {
                    assertThat(quest.progress()).isEqualTo(1);
                    assertThat(quest.target()).isEqualTo(1);
                });
    }

    /** A learner who does triple the goal should see a full bar, not 30/10. */
    @Test
    void clampsProgressToTheTarget() {
        List<Quest> quests = DailyQuests.forToday(new TodayActivity(0, 0, 0, 30, 1));

        assertThat(quests).filteredOn(quest -> quest.kind() == DailyQuestKind.TYPE_SENTENCES).singleElement()
                .satisfies(quest -> assertThat(quest.progress()).isEqualTo(DailyQuests.DAILY_SENTENCE_GOAL));
    }

    @Test
    void measuresTheEveryDayGoalOverAWholeWeek() {
        List<Quest> quests = DailyQuests.forToday(new TodayActivity(0, 0, 0, 0, 5));

        assertThat(quests).filteredOn(quest -> quest.kind() == DailyQuestKind.PRACTISE_EVERY_DAY).singleElement()
                .satisfies(quest -> {
                    assertThat(quest.progress()).isEqualTo(5);
                    assertThat(quest.target()).isEqualTo(DailyQuests.WEEK_DAYS);
                });
    }
}
