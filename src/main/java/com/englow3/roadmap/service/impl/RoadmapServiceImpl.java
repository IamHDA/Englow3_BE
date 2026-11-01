package com.englow3.roadmap.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.roadmap.dto.result.RoadmapResult;
import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapGoalType;
import com.englow3.roadmap.entity.RoadmapItem;
import com.englow3.roadmap.entity.RoadmapMilestone;
import com.englow3.roadmap.entity.RoadmapStage;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;
import com.englow3.roadmap.query.ItemProgressQuery;
import com.englow3.roadmap.repository.LearningRoadmapRepository;
import com.englow3.roadmap.repository.RoadmapAdjustmentRepository;
import com.englow3.roadmap.repository.RoadmapItemRepository;
import com.englow3.roadmap.repository.RoadmapMilestoneRepository;
import com.englow3.roadmap.repository.RoadmapStageRepository;
import com.englow3.roadmap.service.RoadmapPlanner;
import com.englow3.roadmap.service.RoadmapProgression;
import com.englow3.roadmap.service.RoadmapProgression.MilestoneContext;
import com.englow3.roadmap.service.RoadmapService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.LearnerGoalReader.Goal;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoadmapServiceImpl implements RoadmapService {

    /** How far past a stage's planned end the plan is rebuilt around where the learner really is. */
    static final int BEHIND_SCHEDULE_DAYS = 7;

    private final LearningRoadmapRepository roadmapRepo;
    private final RoadmapStageRepository stageRepo;
    private final RoadmapItemRepository itemRepo;
    private final RoadmapMilestoneRepository milestoneRepo;
    private final RoadmapAdjustmentRepository adjustmentRepo;
    private final RoadmapPlanner planner;
    private final RoadmapProgression progression;
    private final ItemProgressQuery itemProgress;
    private final LearnerGoalReader goalReader;
    private final ScoredExamAttemptReader sittings;
    private final UserDirectory userDirectory;
    private final StudyCalendar calendar;
    private final Clock clock;

    @Override
    @Transactional
    public Optional<RoadmapResult> current() {
        UUID userId = userDirectory.requireCurrentUserId();
        Optional<LearningRoadmap> found = roadmapRepo.findOpen(userId)
                .or(() -> roadmapRepo.findFirstByUserIdOrderByCreatedAtDesc(userId));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LearningRoadmap roadmap = found.get();
        if (isFarBehind(roadmap)) {
            LearningRoadmap locked = roadmapRepo.findByIdForUpdate(roadmap.getId()).orElseThrow();
            if (isFarBehind(locked)) {
                planner.tryRequestAdjustment(locked, AdjustmentReason.BEHIND_SCHEDULE);
            }
            roadmap = locked;
        }
        return Optional.of(assemble(roadmap));
    }

    @Override
    @Transactional
    public RoadmapResult create() {
        UUID userId = userDirectory.requireCurrentUserId();
        Goal goal = goalReader.read(userId)
                .orElseThrow(() -> new NotFoundException("LEARNER_PROFILE_NOT_FOUND", "Finish onboarding first"));
        CefrLevel start = CefrLevel.parse(goal.currentLevel())
                .orElseThrow(() -> new BadRequestException("ROADMAP_LEVEL_UNKNOWN",
                        "Your current level is not known yet; take the level test first"));
        CefrLevel targetLevel = CefrLevel.parse(goal.targetLevel()).orElse(null);
        if (goal.certificateLearner() ? goal.targetCertificate() == null || goal.targetScore() == null
                : targetLevel == null) {
            throw new BadRequestException("ROADMAP_GOAL_INCOMPLETE", "Set your goal before asking for a roadmap");
        }

        Optional<LearningRoadmap> open = roadmapRepo.findOpen(userId)
                .flatMap(found -> roadmapRepo.findByIdForUpdate(found.getId()));
        if (open.isPresent()) {
            LearningRoadmap roadmap = open.get();
            if (sameKind(roadmap, goal)) {
                if (sameTarget(roadmap, goal, targetLevel)) {
                    return assemble(roadmap);
                }
                if (roadmap.getStatus() == RoadmapStatus.GENERATING) {
                    throw new ConflictException("ROADMAP_GENERATING", "Your roadmap is still being made");
                }
                roadmap.changeGoal(goal.targetScore(), targetLevel, goal.targetDate(), goal.dailyStudyMinutes());
                planner.requestAdjustment(roadmap, AdjustmentReason.GOAL_CHANGED);
                return assemble(roadmap);
            }
            roadmap.supersede(clock.instant());
            // Written now: the partial unique index allows one open roadmap, and the new one is inserted next.
            roadmapRepo.saveAndFlush(roadmap);
        }

        UUID token = UUID.randomUUID();
        LearningRoadmap roadmap = goal.certificateLearner()
                ? LearningRoadmap.forCertificate(UUID.randomUUID(), userId, goal.targetCertificate(),
                        goal.targetScore(), goal.targetDate(), start, goal.currentScore(), goal.dailyStudyMinutes(),
                        token)
                : LearningRoadmap.forLevel(UUID.randomUUID(), userId, targetLevel, goal.targetDate(), start,
                        goal.dailyStudyMinutes(), token);
        roadmapRepo.saveAndFlush(roadmap);
        planner.requestFirstPlan(roadmap);
        return assemble(roadmap);
    }

    @Override
    @Transactional
    public void abandon() {
        UUID userId = userDirectory.requireCurrentUserId();
        LearningRoadmap roadmap = roadmapRepo.findOpen(userId)
                .flatMap(found -> roadmapRepo.findByIdForUpdate(found.getId()))
                .orElseThrow(() -> new NotFoundException("ROADMAP_NOT_FOUND", "You have no roadmap in progress"));
        roadmap.abandon(clock.instant());
    }

    @Override
    @Transactional
    public RoadmapResult recordMockSitting(UUID milestoneId, UUID examAttemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        MilestoneContext context = progression.locate(userId, milestoneId);
        var sitting = sittings.read(examAttemptId, userId)
                .orElseThrow(() -> new NotFoundException("EXAM_ATTEMPT_NOT_FOUND",
                        "No sitting %s of yours".formatted(examAttemptId)));
        if (sitting.scoredAt() != null && sitting.scoredAt().isBefore(context.stage().getCreatedAt())) {
            throw new BadRequestException("MILESTONE_SITTING_TOO_OLD",
                    "Only a sitting taken since this stage began counts");
        }
        context.milestone().recordMockSitting(examAttemptId, sitting.examId(), sitting.scored(), sitting.full(),
                sitting.convertedScore(), clock.instant());
        progression.settle(context);
        return assemble(context.roadmap());
    }

    /**
     * The current stage ran a week past its planned end, nothing is being replanned, and no automatic replan failed
     * today already - otherwise every visit would spend another of the day's attempts on the same failure.
     */
    private boolean isFarBehind(LearningRoadmap roadmap) {
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE || roadmap.isAdjusting()) {
            return false;
        }
        if (roadmap.getFailureCode() != null && roadmap.getUpdatedAt() != null
                && !calendar.dayOf(roadmap.getUpdatedAt()).isBefore(calendar.today())) {
            return false;
        }
        LocalDate today = calendar.today();
        return stageRepo.findFirstByRoadmapIdAndStatus(roadmap.getId(), RoadmapStageStatus.CURRENT)
                .map(stage -> stage.getPlannedEnd().plusDays(BEHIND_SCHEDULE_DAYS).isBefore(today)).orElse(false);
    }

    private static boolean sameKind(LearningRoadmap roadmap, Goal goal) {
        return goal.certificateLearner()
                ? roadmap.getGoalType() == RoadmapGoalType.CERTIFICATE
                        && Objects.equals(roadmap.getTargetCertificate(), goal.targetCertificate())
                : roadmap.getGoalType() == RoadmapGoalType.LEVEL;
    }

    private static boolean sameTarget(LearningRoadmap roadmap, Goal goal, CefrLevel targetLevel) {
        boolean sameFinish = roadmap.getGoalType() == RoadmapGoalType.CERTIFICATE
                ? roadmap.getTargetScore().compareTo(goal.targetScore()) == 0
                : roadmap.getTargetLevel() == targetLevel;
        return sameFinish && Objects.equals(roadmap.getTargetDate(), goal.targetDate())
                && Objects.equals(roadmap.getDailyMinutes(), goal.dailyStudyMinutes());
    }

    private RoadmapResult assemble(LearningRoadmap roadmap) {
        List<RoadmapStage> stages = stageRepo.findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(roadmap.getId()).stream()
                .filter(RoadmapStage::isInForce).toList();
        List<UUID> stageIds = stages.stream().map(RoadmapStage::getId).toList();
        Map<UUID, List<RoadmapItem>> itemsByStage = stageIds.isEmpty() ? Map.of()
                : itemRepo.findByStageIdInOrderByStageIdAscOrderNoAsc(stageIds).stream()
                        .collect(Collectors.groupingBy(RoadmapItem::getStageId));
        Map<UUID, RoadmapMilestone> milestones = stageIds.isEmpty() ? Map.of()
                : milestoneRepo.findByStageIdIn(stageIds).stream()
                        .collect(Collectors.toMap(RoadmapMilestone::getStageId, Function.identity()));
        Map<RoadmapContentKind, List<UUID>> byKind = new EnumMap<>(RoadmapContentKind.class);
        itemsByStage.values().stream().flatMap(List::stream).forEach(item -> byKind
                .computeIfAbsent(item.getContentKind(), kind -> new ArrayList<>()).add(item.getContentId()));
        Map<UUID, Integer> done = itemProgress.percentDone(roadmap.getUserId(), byKind);

        LocalDate today = calendar.today();
        String schedule = stages.stream().filter(stage -> stage.getStatus() == RoadmapStageStatus.CURRENT)
                .anyMatch(stage -> stage.getPlannedEnd().isBefore(today)) ? "BEHIND" : "ON_TRACK";
        List<RoadmapResult.Stage> stageResults = stages.stream().map(stage -> stage(stage,
                itemsByStage.getOrDefault(stage.getId(), List.of()), milestones.get(stage.getId()), done)).toList();
        List<RoadmapResult.Adjustment> adjustments = adjustmentRepo
                .findTop10ByRoadmapIdOrderByCreatedAtDesc(roadmap.getId()).stream()
                .map(adjustment -> new RoadmapResult.Adjustment(adjustment.getReason().name(),
                        adjustment.getCreatedAt()))
                .toList();
        return new RoadmapResult(roadmap.getId(), roadmap.getStatus().name(), roadmap.getGoalType().name(),
                roadmap.getTargetCertificate(), roadmap.getTargetScore(), name(roadmap.getTargetLevel()),
                roadmap.getTargetDate(), name(roadmap.getStartLevel()), roadmap.getStartScore(),
                roadmap.getDailyMinutes(), roadmap.isAdjusting(), name(roadmap.getPendingReason()),
                roadmap.getFailureCode(), schedule, roadmap.getCreatedAt(), roadmap.getCompletedAt(), stageResults,
                adjustments);
    }

    private static RoadmapResult.Stage stage(RoadmapStage stage, List<RoadmapItem> items, RoadmapMilestone milestone,
            Map<UUID, Integer> done) {
        List<RoadmapResult.Item> itemResults = items.stream().map(item -> {
            int percent = done.getOrDefault(item.getContentId(), 0);
            return new RoadmapResult.Item(item.getId(), item.getContentKind().name(), item.getContentId(),
                    item.getTitle(), item.getSkill(), item.isRequired(), item.getTargetPercent(), item.getPlannedWeek(),
                    item.getEstimatedMinutes(), percent, percent >= item.getTargetPercent());
        }).toList();
        List<RoadmapResult.Item> required = itemResults.stream().filter(RoadmapResult.Item::required).toList();
        int percentDone = required.isEmpty() ? 100
                : (int) required.stream()
                        .mapToInt(item -> Math.min(100, item.percentDone() * 100 / item.targetPercent())).average()
                        .orElse(0);
        boolean ready = required.stream().allMatch(RoadmapResult.Item::done);
        RoadmapResult.Milestone milestoneResult = milestone == null ? null
                : new RoadmapResult.Milestone(milestone.getId(), milestone.getKind().name(), milestone.getExamId(),
                        milestone.getRequiredScore(), name(milestone.getRequiredLevel()), milestone.getStatus().name(),
                        milestone.getResultScore(), name(milestone.getResultLevel()), milestone.getAttemptCount(),
                        milestone.getAchievedAt(), ready);
        List<String> skills = stage.getFocusSkills().isBlank() ? List.of()
                : Arrays.asList(stage.getFocusSkills().split(","));
        return new RoadmapResult.Stage(stage.getId(), stage.getOrderNo(), stage.getTitle(), stage.getSummary(),
                stage.getFromLevel().name(), stage.getToLevel().name(), skills, stage.getPlannedStart(),
                stage.getPlannedEnd(), stage.getStatus().name(), percentDone, itemResults, milestoneResult);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
