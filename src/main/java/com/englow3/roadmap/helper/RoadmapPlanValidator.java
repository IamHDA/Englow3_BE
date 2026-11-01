package com.englow3.roadmap.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapGoalType;
import com.englow3.roadmap.helper.RoadmapPlan.Candidate;
import com.englow3.roadmap.helper.RoadmapPlan.Context;
import com.englow3.roadmap.helper.RoadmapPlan.MockPaper;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedItem;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedMilestone;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedStage;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads the AI's proposal and refuses it unless every claim it makes can be checked and holds. The model chooses; it
 * does not get to invent content, skip a level, run past the deadline or ask for more time than the learner has:
 * <ol>
 * <li>every item is one of the candidates sent;</li>
 * <li>the stages climb without gaps from the starting level and end at the goal;</li>
 * <li>the dates run from today to the target date without overlapping;</li>
 * <li>each stage has exactly one milestone, a mock paper that was offered or the level the stage reaches;</li>
 * <li>no week asks for more than the learner's daily minutes times seven.</li>
 * </ol>
 * A refusal is an {@code ROADMAP_PLAN_INVALID} with the rule broken, which the handler treats as worth another try.
 */
public final class RoadmapPlanValidator {

    static final int MAX_STAGES = 12;
    static final int MAX_ITEMS_PER_STAGE = 40;
    static final int MAX_PLAN_DAYS = 730;

    private RoadmapPlanValidator() {
    }

    public static List<ProposedStage> validate(JsonNode proposal, Context context) {
        JsonNode stagesNode = proposal.path("stages");
        if (!stagesNode.isArray() || stagesNode.isEmpty() || stagesNode.size() > MAX_STAGES) {
            throw invalid("the plan needs between 1 and %d stages".formatted(MAX_STAGES));
        }
        Map<UUID, Candidate> candidates = context.candidates().stream()
                .collect(Collectors.toMap(Candidate::id, Function.identity(), (a, b) -> a));
        Map<UUID, MockPaper> papers = context.mockPapers().stream()
                .collect(Collectors.toMap(MockPaper::id, Function.identity(), (a, b) -> a));
        BigDecimal scoreCeiling = scoreCeiling(context.targetCertificate());
        int weeklyMinutes = context.dailyMinutes() * 7;

        List<ProposedStage> stages = new ArrayList<>();
        CefrLevel expectedFrom = context.startLevel();
        LocalDate earliestStart = context.today();
        BigDecimal lastScore = context.minimumScore() == null ? BigDecimal.ZERO : context.minimumScore();
        for (JsonNode node : stagesNode) {
            int position = stages.size() + 1;
            CefrLevel from = level(node.path("fromLevel"), "stage %d fromLevel".formatted(position));
            CefrLevel to = level(node.path("toLevel"), "stage %d toLevel".formatted(position));
            if (from != expectedFrom) {
                throw invalid("stage %d starts at %s, expected %s".formatted(position, from, expectedFrom));
            }
            if (to.compareTo(from) < 0) {
                throw invalid("stage %d goes down a level".formatted(position));
            }
            LocalDate start = date(node.path("plannedStart"), "stage %d plannedStart".formatted(position));
            LocalDate end = date(node.path("plannedEnd"), "stage %d plannedEnd".formatted(position));
            if (start.isBefore(earliestStart)) {
                throw invalid("stage %d starts %s, before %s".formatted(position, start, earliestStart));
            }
            if (end.isBefore(start)) {
                throw invalid("stage %d ends before it starts".formatted(position));
            }
            if (context.targetDate() != null && end.isAfter(context.targetDate())) {
                throw invalid("stage %d ends after the target date".formatted(position));
            }
            if (ChronoUnit.DAYS.between(context.today(), end) > MAX_PLAN_DAYS) {
                throw invalid("stage %d ends more than two years away".formatted(position));
            }
            int weeks = (int) ((ChronoUnit.DAYS.between(start, end) + 7) / 7);

            List<ProposedItem> items = items(node.path("items"), position, weeks, candidates, weeklyMinutes);
            ProposedMilestone milestone = milestone(node.path("milestone"), position, to, context, papers, scoreCeiling,
                    lastScore);
            if (milestone.requiredScore() != null) {
                lastScore = milestone.requiredScore();
            }
            stages.add(new ProposedStage(text(node.path("title"), 200), text(node.path("summary"), 2000), from, to,
                    skills(node.path("focusSkills")), start, end, items, milestone));
            expectedFrom = to;
            earliestStart = end.plusDays(1);
        }

        if (context.goalType() == RoadmapGoalType.LEVEL && expectedFrom != context.targetLevel()) {
            throw invalid("the last stage ends at %s, not the goal %s".formatted(expectedFrom, context.targetLevel()));
        }
        if (context.goalType() == RoadmapGoalType.CERTIFICATE && lastScore.compareTo(context.targetScore()) < 0) {
            throw invalid(
                    "the last milestone asks for %s, below the goal %s".formatted(lastScore, context.targetScore()));
        }
        return stages;
    }

    private static List<ProposedItem> items(JsonNode node, int position, int weeks, Map<UUID, Candidate> candidates,
            int weeklyMinutes) {
        if (!node.isArray() || node.isEmpty() || node.size() > MAX_ITEMS_PER_STAGE) {
            throw invalid("stage %d needs between 1 and %d items".formatted(position, MAX_ITEMS_PER_STAGE));
        }
        List<ProposedItem> items = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Map<Integer, Integer> minutesByWeek = new HashMap<>();
        for (JsonNode item : node) {
            UUID contentId = uuid(item.path("contentId"), "stage %d item contentId".formatted(position));
            Candidate candidate = candidates.get(contentId);
            RoadmapContentKind kind = kind(item.path("kind"));
            if (candidate == null || candidate.kind() != kind) {
                throw invalid("stage %d schedules content that was not offered: %s".formatted(position, contentId));
            }
            if (!seen.add(contentId)) {
                throw invalid("stage %d schedules the same content twice".formatted(position));
            }
            int week = item.path("plannedWeek").asInt(1);
            if (week < 1 || week > weeks) {
                throw invalid("stage %d puts an item in week %d of %d".formatted(position, week, weeks));
            }
            int target = item.path("targetPercent").asInt(80);
            if (target < 1 || target > 100) {
                throw invalid("stage %d has a target outside 1-100%%".formatted(position));
            }
            int minutes = minutesByWeek.merge(week, candidate.estimatedMinutes(), Integer::sum);
            if (minutes > weeklyMinutes) {
                throw invalid("stage %d week %d needs %d minutes, more than %d available".formatted(position, week,
                        minutes, weeklyMinutes));
            }
            items.add(new ProposedItem(kind, contentId, candidate.title(), item.path("required").asBoolean(true),
                    target, week, candidate.estimatedMinutes()));
        }
        return items;
    }

    private static ProposedMilestone milestone(JsonNode node, int position, CefrLevel to, Context context,
            Map<UUID, MockPaper> papers, BigDecimal scoreCeiling, BigDecimal lastScore) {
        if (!node.isObject()) {
            throw invalid("stage %d has no milestone".formatted(position));
        }
        if (context.goalType() == RoadmapGoalType.LEVEL) {
            // The level check measures the band the stage set out to reach; anything else would be a different stage.
            return new ProposedMilestone(null, null, to);
        }
        UUID examId = uuid(node.path("examId"), "stage %d milestone examId".formatted(position));
        if (!papers.containsKey(examId)) {
            throw invalid("stage %d milestone is a paper that was not offered".formatted(position));
        }
        JsonNode scoreNode = node.path("requiredScore");
        if (!scoreNode.isNumber()) {
            throw invalid("stage %d milestone has no score".formatted(position));
        }
        BigDecimal score = scoreNode.decimalValue().stripTrailingZeros();
        if (score.signum() <= 0 || score.compareTo(scoreCeiling) > 0) {
            throw invalid("stage %d milestone score %s is off the scale".formatted(position, score));
        }
        if (score.compareTo(lastScore) < 0) {
            throw invalid("stage %d milestone asks for less than the stage before".formatted(position));
        }
        return new ProposedMilestone(examId, score, null);
    }

    static BigDecimal scoreCeiling(String certificate) {
        return "TOEIC".equals(certificate) ? BigDecimal.valueOf(990) : BigDecimal.valueOf(9);
    }

    private static List<String> skills(JsonNode node) {
        List<String> skills = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode skill : node) {
                String value = skill.asText("").strip().toUpperCase(Locale.ROOT);
                if (value.matches("[A-Z_]{2,30}") && !skills.contains(value)) {
                    skills.add(value);
                }
            }
        }
        return skills.size() > 6 ? skills.subList(0, 6) : skills;
    }

    private static RoadmapContentKind kind(JsonNode node) {
        try {
            return RoadmapContentKind.valueOf(node.asText(""));
        } catch (IllegalArgumentException unknown) {
            throw invalid("unknown content kind " + node.asText(""));
        }
    }

    private static CefrLevel level(JsonNode node, String field) {
        return CefrLevel.parse(node.asText(null)).orElseThrow(() -> invalid(field + " is not a CEFR level"));
    }

    private static LocalDate date(JsonNode node, String field) {
        try {
            return LocalDate.parse(node.asText(""));
        } catch (java.time.format.DateTimeParseException bad) {
            throw invalid(field + " is not a date");
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.asText(""));
        } catch (IllegalArgumentException bad) {
            throw invalid(field + " is not an id");
        }
    }

    private static String text(JsonNode node, int max) {
        String value = node.asText("").strip();
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static BadRequestException invalid(String rule) {
        return new BadRequestException("ROADMAP_PLAN_INVALID", "The proposed roadmap was refused: " + rule);
    }
}
