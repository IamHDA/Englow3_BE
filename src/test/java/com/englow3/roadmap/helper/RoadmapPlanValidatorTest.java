package com.englow3.roadmap.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapGoalType;
import com.englow3.roadmap.helper.RoadmapPlan.Candidate;
import com.englow3.roadmap.helper.RoadmapPlan.Context;
import com.englow3.roadmap.helper.RoadmapPlan.MockPaper;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RoadmapPlanValidatorTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-02");
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID quiz = UUID.randomUUID();
    private final UUID lesson = UUID.randomUUID();
    private final UUID heavy = UUID.randomUUID();
    private final UUID paper = UUID.randomUUID();
    private final List<Candidate> candidates = List.of(
            new Candidate(RoadmapContentKind.QUIZ, quiz, "Thì hiện tại", CefrLevel.A2, "GRAMMAR", 15),
            new Candidate(RoadmapContentKind.DICTATION_LESSON, lesson, "Ở sân bay", CefrLevel.B1, "LISTENING", 20),
            new Candidate(RoadmapContentKind.ASSESSMENT_TASK, heavy, "Bài viết", CefrLevel.B1, "WRITING", 200));

    private Context levelGoal() {
        return new Context(RoadmapGoalType.LEVEL, CefrLevel.A2, CefrLevel.B2, null, null, BigDecimal.ZERO, TODAY,
                LocalDate.parse("2027-06-01"), 30, 1, candidates, List.of());
    }

    private Context certificateGoal() {
        return new Context(RoadmapGoalType.CERTIFICATE, CefrLevel.B1, null, "IELTS", new BigDecimal("6.5"),
                BigDecimal.ZERO, TODAY, null, 30, 1, candidates, List.of(new MockPaper(paper, "Mock 1", "ACADEMIC")));
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text.replace('\'', '"'));
    }

    private String item(UUID id, String kind, int week) {
        return "{'kind':'%s','contentId':'%s','plannedWeek':%d}".formatted(kind, id, week);
    }

    private String stage(String from, String to, String start, String end, String items, String milestone) {
        return "{'title':'t','fromLevel':'%s','toLevel':'%s','plannedStart':'%s','plannedEnd':'%s','items':[%s],'milestone':%s}"
                .formatted(from, to, start, end, items, milestone);
    }

    private String twoStageLevelPlan() {
        return "{'stages':[" + stage("A2", "B1", "2026-11-02", "2026-12-13", item(quiz, "QUIZ", 1), "{}") + ","
                + stage("B1", "B2", "2026-12-14", "2027-02-28", item(lesson, "DICTATION_LESSON", 2), "{}") + "]}";
    }

    @Test
    void acceptsAPlanThatClimbsToTheGoalOnTime() throws Exception {
        List<ProposedStage> stages = RoadmapPlanValidator.validate(json(twoStageLevelPlan()), levelGoal());

        assertThat(stages).hasSize(2);
        assertThat(stages.get(1).milestone().requiredLevel()).isEqualTo(CefrLevel.B2);
        assertThat(stages.get(0).items().getFirst().title()).isEqualTo("Thì hiện tại");
        assertThat(stages.get(0).items().getFirst().targetPercent()).isEqualTo(80);
    }

    @Test
    void refusesContentThatWasNotOffered() throws Exception {
        String plan = "{'stages':["
                + stage("A2", "B2", "2026-11-02", "2026-12-13", item(UUID.randomUUID(), "QUIZ", 1), "{}") + "]}";
        assertRefused(plan, levelGoal(), "not offered");
        String wrongKind = "{'stages':["
                + stage("A2", "B2", "2026-11-02", "2026-12-13", item(quiz, "FLASHCARD_SET", 1), "{}") + "]}";
        assertRefused(wrongKind, levelGoal(), "not offered");
    }

    @Test
    void refusesAGapInLevelsOrAPlanThatStopsShortOfTheGoal() throws Exception {
        String gap = "{'stages':[" + stage("A2", "B1", "2026-11-02", "2026-12-13", item(quiz, "QUIZ", 1), "{}") + ","
                + stage("B2", "C1", "2026-12-14", "2027-02-28", item(lesson, "DICTATION_LESSON", 1), "{}") + "]}";
        assertRefused(gap, levelGoal(), "expected B1");
        String shortOfGoal = "{'stages':[" + stage("A2", "B1", "2026-11-02", "2026-12-13", item(quiz, "QUIZ", 1), "{}")
                + "]}";
        assertRefused(shortOfGoal, levelGoal(), "not the goal B2");
    }

    @Test
    void refusesDatesBeforeTodayOverlappingOrPastTheDeadline() throws Exception {
        assertRefused("{'stages':[" + stage("A2", "B2", "2026-10-01", "2026-12-13", item(quiz, "QUIZ", 1), "{}") + "]}",
                levelGoal(), "before");
        String overlap = "{'stages':[" + stage("A2", "B1", "2026-11-02", "2026-12-13", item(quiz, "QUIZ", 1), "{}")
                + "," + stage("B1", "B2", "2026-12-10", "2027-02-28", item(lesson, "DICTATION_LESSON", 1), "{}") + "]}";
        assertRefused(overlap, levelGoal(), "before");
        assertRefused("{'stages':[" + stage("A2", "B2", "2026-11-02", "2027-07-01", item(quiz, "QUIZ", 1), "{}") + "]}",
                levelGoal(), "after the target date");
    }

    @Test
    void refusesAWeekThatAsksForMoreTimeThanTheLearnerHas() throws Exception {
        String plan = "{'stages':[" + stage("A2", "B2", "2026-11-02", "2026-12-13",
                item(quiz, "QUIZ", 1) + "," + item(heavy, "ASSESSMENT_TASK", 1), "{}") + "]}";
        assertRefused(plan, levelGoal(), "more than 210 available");
    }

    @Test
    void refusesAnItemInAWeekTheStageDoesNotHave() throws Exception {
        String plan = "{'stages':[" + stage("A2", "B2", "2026-11-02", "2026-11-15", item(quiz, "QUIZ", 3), "{}") + "]}";
        assertRefused(plan, levelGoal(), "week 3 of 2");
    }

    @Test
    void aCertificatePlanEndsAtTheTargetScoreOnOfferedPapers() throws Exception {
        String ok = "{'stages':["
                + stage("B1", "B2", "2026-11-02", "2026-12-13", item(quiz, "QUIZ", 1),
                        "{'examId':'%s','requiredScore':5.5}".formatted(paper))
                + "," + stage("B2", "B2", "2026-12-14", "2027-02-28", item(lesson, "DICTATION_LESSON", 1),
                        "{'examId':'%s','requiredScore':6.5}".formatted(paper))
                + "]}";
        List<ProposedStage> stages = RoadmapPlanValidator.validate(json(ok), certificateGoal());
        assertThat(stages.get(1).milestone().requiredScore()).isEqualByComparingTo("6.5");

        assertRefused(ok.replace("6.5", "6.0"), certificateGoal(), "below the goal");
        assertRefused(ok.replace(paper.toString(), UUID.randomUUID().toString()), certificateGoal(), "not offered");
        assertRefused(ok.replace("6.5", "10"), certificateGoal(), "off the scale");
        assertRefused(ok.replace("5.5", "7.0"), certificateGoal(), "less than the stage before");
    }

    private void assertRefused(String plan, Context context, String reason) throws Exception {
        JsonNode proposal = json(plan);
        assertThatThrownBy(() -> RoadmapPlanValidator.validate(proposal, context))
                .hasFieldOrPropertyWithValue("code", "ROADMAP_PLAN_INVALID").hasMessageContaining(reason);
    }
}
