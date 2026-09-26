package com.englow3.progress.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.progress.entity.DailyTaskKind;
import com.englow3.progress.entity.DailyTaskStatus;
import com.englow3.progress.helper.DailyPlan.Candidate;
import com.englow3.progress.helper.DailyPlan.Finished;
import com.englow3.progress.helper.DailyPlan.Node;

class DailyPlanTest {

    private static final UUID SET = UUID.randomUUID();
    private static final UUID LESSON = UUID.randomUUID();
    private static final UUID QUIZ = UUID.randomUUID();

    @Test
    void plansNothingForALearnerWithNoWorkAndNoHistory() {
        assertThat(DailyPlan.build(List.of(), List.of())).isEmpty();
    }

    @Test
    void marksOnlyTheFirstOutstandingTaskAsCurrent() {
        List<Node> nodes = DailyPlan.build(List.of(),
                List.of(new Candidate(DailyTaskKind.FLASHCARD_REVIEW, SET, "Core 500", 12, 0, 40),
                        new Candidate(DailyTaskKind.QUIZ, QUIZ, "Tenses", 10, 0, null)));

        assertThat(nodes).extracting(Node::status).containsExactly(DailyTaskStatus.CURRENT, DailyTaskStatus.UPCOMING);
    }

    /** Due cards come before anything else: they are the only work in the product with a schedule behind it. */
    @Test
    void putsScheduledReviewsAheadOfEverythingElse() {
        List<Node> nodes = DailyPlan.build(List.of(),
                List.of(new Candidate(DailyTaskKind.QUIZ, QUIZ, "Tenses", 10, 0, null),
                        new Candidate(DailyTaskKind.DICTATION, LESSON, "Airport", 4, 0, 20),
                        new Candidate(DailyTaskKind.FLASHCARD_REVIEW, SET, "Core 500", 12, 0, 40)));

        assertThat(nodes).extracting(Node::kind).containsExactly(DailyTaskKind.FLASHCARD_REVIEW,
                DailyTaskKind.DICTATION, DailyTaskKind.QUIZ);
    }

    @Test
    void listsWorkFinishedTodayAsCompletedBeforeWhatIsLeft() {
        List<Node> nodes = DailyPlan.build(List.of(new Finished(DailyTaskKind.DICTATION, LESSON, "Airport", 6, 88)),
                List.of(new Candidate(DailyTaskKind.QUIZ, QUIZ, "Tenses", 10, 0, null)));

        assertThat(nodes).extracting(Node::status).containsExactly(DailyTaskStatus.COMPLETED, DailyTaskStatus.CURRENT);
        assertThat(nodes).extracting(Node::order).containsExactly(1, 2);
    }

    /**
     * The case that decides whether the roadmap contradicts itself. A set worked on this morning that still has cards
     * due must appear once, as work to finish - not as a tick and a to-do for the same set.
     */
    @Test
    void showsAPartlyFinishedSetOnceAsOutstanding() {
        List<Node> nodes = DailyPlan.build(
                List.of(new Finished(DailyTaskKind.FLASHCARD_REVIEW, SET, "Core 500", 5, 70)),
                List.of(new Candidate(DailyTaskKind.FLASHCARD_REVIEW, SET, "Core 500", 12, 5, 40)));

        assertThat(nodes).singleElement().satisfies(node -> {
            assertThat(node.status()).isEqualTo(DailyTaskStatus.CURRENT);
            assertThat(node.unitsRemaining()).isEqualTo(12);
            assertThat(node.unitsDoneToday()).isEqualTo(5);
        });
    }

    /** Same target id, different kind, is a different thing. Nothing should collapse them. */
    @Test
    void doesNotConfuseTwoKindsThatShareAnId() {
        UUID shared = UUID.randomUUID();

        List<Node> nodes = DailyPlan.build(List.of(new Finished(DailyTaskKind.DICTATION, shared, "Airport", 6, 88)),
                List.of(new Candidate(DailyTaskKind.QUIZ, shared, "Tenses", 10, 0, null)));

        assertThat(nodes).hasSize(2);
    }

    @Test
    void advertisesTheRewardTheWorkActuallyPays() {
        List<Node> nodes = DailyPlan.build(List.of(),
                List.of(new Candidate(DailyTaskKind.FLASHCARD_REVIEW, SET, "Core 500", 12, 0, 40),
                        new Candidate(DailyTaskKind.DICTATION, LESSON, "Airport", 4, 0, 20),
                        new Candidate(DailyTaskKind.QUIZ, QUIZ, "Tenses", 10, 0, null)));

        assertThat(nodes).extracting(Node::xpReward).containsExactly(12L * ExperiencePoints.FLASHCARD_REVIEW_XP,
                4L * ExperiencePoints.DICTATION_SENTENCE_XP, (long) ExperiencePoints.QUIZ_ATTEMPT_XP);
    }

    /** A quiz pays per submission, so a ten-question quiz and a thirty-question quiz are worth the same. */
    @Test
    void paysPerQuizRatherThanPerQuestion() {
        assertThat(DailyPlan.xpFor(DailyTaskKind.QUIZ, 30)).isEqualTo(DailyPlan.xpFor(DailyTaskKind.QUIZ, 10));
    }

    @Test
    void promisesNothingForWorkAlreadyDone() {
        List<Node> nodes = DailyPlan.build(List.of(new Finished(DailyTaskKind.QUIZ, QUIZ, "Tenses", 1, 92)), List.of());

        assertThat(nodes).singleElement().satisfies(node -> {
            assertThat(node.xpReward()).isZero();
            assertThat(node.unitsRemaining()).isZero();
            assertThat(node.completionPercent()).isEqualTo(92);
        });
    }
}
