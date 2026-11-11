package com.englow3.roadmap.service.impl;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.MilestoneKind;
import com.englow3.roadmap.entity.MilestoneStatus;
import com.englow3.roadmap.entity.RoadmapAdjustment;
import com.englow3.roadmap.entity.RoadmapMilestone;
import com.englow3.roadmap.entity.RoadmapStage;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;
import com.englow3.roadmap.repository.LearningRoadmapRepository;
import com.englow3.roadmap.repository.RoadmapAdjustmentRepository;
import com.englow3.roadmap.repository.RoadmapMilestoneRepository;
import com.englow3.roadmap.repository.RoadmapStageRepository;
import com.englow3.roadmap.service.RoadmapPlanner;
import com.englow3.roadmap.service.RoadmapProgression;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.time.StudyCalendar;

import lombok.RequiredArgsConstructor;

/** Joins the caller's transaction; every method expects one to be open. */
@Service
@RequiredArgsConstructor
public class RoadmapProgressionImpl implements RoadmapProgression {

    /** How far past a stage's planned end the plan is rebuilt around where the learner really is. */
    static final int BEHIND_SCHEDULE_DAYS = 7;

    private final LearningRoadmapRepository roadmapRepo;
    private final RoadmapStageRepository stageRepo;
    private final RoadmapMilestoneRepository milestoneRepo;
    private final RoadmapAdjustmentRepository adjustmentRepo;
    private final RoadmapPlanner planner;
    private final ScoredExamAttemptReader sittings;
    private final com.englow3.exam.api.CompletedFullTestReader fullTests;
    private final Clock clock;
    private final StudyCalendar calendar;

    @Override
    public MilestoneContext locate(UUID userId, UUID milestoneId) {
        RoadmapMilestone milestone = milestoneRepo.findById(milestoneId).orElseThrow(() -> notFound(milestoneId));
        RoadmapStage stage = stageRepo.findById(milestone.getStageId()).orElseThrow(() -> notFound(milestoneId));
        // Roadmap first, then milestone: every writer locks in this order.
        LearningRoadmap roadmap = roadmapRepo.findByIdForUpdate(stage.getRoadmapId())
                .filter(found -> found.getUserId().equals(userId)).orElseThrow(() -> notFound(milestoneId));
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE) {
            throw new ConflictException("ROADMAP_NOT_ACTIVE", "This roadmap is not active");
        }
        if (stage.getStatus() != RoadmapStageStatus.CURRENT) {
            throw new ConflictException("MILESTONE_NOT_CURRENT", "Only the current stage's milestone can be taken");
        }
        return new MilestoneContext(roadmap, stage,
                milestoneRepo.findByIdForUpdate(milestoneId).orElseThrow(() -> notFound(milestoneId)));
    }

    @Override
    public Optional<MilestoneContext> find(UUID userId, UUID milestoneId) {
        try {
            return Optional.of(locate(userId, milestoneId));
        } catch (NotFoundException | ConflictException movedOn) {
            return Optional.empty();
        }
    }

    @Override
    public LearningRoadmap catchUp(LearningRoadmap roadmap) {
        LearningRoadmap current = recordLatestMockSitting(roadmap).map(MilestoneContext::roadmap).orElse(roadmap);
        if (isFarBehind(current)) {
            LearningRoadmap locked = roadmapRepo.findByIdForUpdate(current.getId()).orElseThrow();
            if (isFarBehind(locked)) {
                planner.tryRequestAdjustment(locked, AdjustmentReason.BEHIND_SCHEDULE);
            }
            current = locked;
        }
        return current;
    }

    /**
     * The current stage ran a week past its planned end, nothing is being replanned, and no automatic replan failed
     * today already - otherwise every check would spend another of the day's attempts on the same failure.
     */
    private boolean isFarBehind(LearningRoadmap roadmap) {
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE || roadmap.isAdjusting()) {
            return false;
        }
        if (roadmap.getFailureCode() != null && roadmap.getUpdatedAt() != null
                && !calendar.dayOf(roadmap.getUpdatedAt()).isBefore(calendar.today())) {
            return false;
        }
        java.time.LocalDate today = calendar.today();
        return stageRepo.findFirstByRoadmapIdAndStatus(roadmap.getId(), RoadmapStageStatus.CURRENT)
                .map(stage -> stage.getPlannedEnd().plusDays(BEHIND_SCHEDULE_DAYS).isBefore(today)).orElse(false);
    }

    @Override
    public Optional<MilestoneContext> recordLatestMockSitting(LearningRoadmap roadmap) {
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE) {
            return Optional.empty();
        }
        Optional<RoadmapStage> current = stageRepo.findFirstByRoadmapIdAndStatus(roadmap.getId(),
                RoadmapStageStatus.CURRENT);
        Optional<RoadmapMilestone> milestone = current.flatMap(stage -> milestoneRepo.findByStageId(stage.getId()))
                .filter(found -> found.getKind() == MilestoneKind.CERTIFICATE_MOCK)
                .filter(found -> found.getStatus() != MilestoneStatus.PASSED);
        if (milestone.isEmpty()) {
            return Optional.empty();
        }
        if (milestone.get().isFullTest()) {
            return recordLatestFullTest(roadmap, current.get(), milestone.get());
        }
        var sitting = sittings.latestFullSitting(milestone.get().getExamId(), roadmap.getUserId(),
                openedAt(current.get()));
        if (sitting.isEmpty() || sitting.get().attemptId().equals(milestone.get().getResultExamAttemptId())
                || sitting.get().convertedScore() == null) {
            return Optional.empty();
        }
        MilestoneContext context = locate(roadmap.getUserId(), milestone.get().getId());
        if (sitting.get().attemptId().equals(context.milestone().getResultExamAttemptId())
                || milestoneRepo.wasUsedInPassedMilestone(roadmap.getId(), sitting.get().attemptId())) {
            return Optional.empty();
        }
        context.milestone().recordMockSitting(sitting.get().attemptId(), sitting.get().examId(), sitting.get().scored(),
                sitting.get().full(), sitting.get().convertedScore(), clock.instant());
        settle(context);
        return Optional.of(context);
    }

    /** An IELTS mock milestone moves on when a sitting of its full test, taken since the stage opened, completes. */
    private Optional<MilestoneContext> recordLatestFullTest(LearningRoadmap roadmap, RoadmapStage stage,
            RoadmapMilestone milestone) {
        var completed = fullTests.latestCompleted(milestone.getFullTestId(), roadmap.getUserId(), openedAt(stage));
        if (completed.isEmpty() || completed.get().sittingId().equals(milestone.getResultFullTestSittingId())) {
            return Optional.empty();
        }
        MilestoneContext context = locate(roadmap.getUserId(), milestone.getId());
        if (completed.get().sittingId().equals(context.milestone().getResultFullTestSittingId())
                || milestoneRepo.wasUsedInPassedMilestone(roadmap.getId(), completed.get().sittingId())) {
            return Optional.empty();
        }
        context.milestone().recordFullTestSitting(completed.get().sittingId(), completed.get().fullTestId(),
                completed.get().overallBand(), clock.instant());
        settle(context);
        return Optional.of(context);
    }

    @Override
    public java.time.Instant openedAt(RoadmapStage stage) {
        java.time.Instant opened = stage.getCreatedAt();
        Optional<java.time.Instant> previousPass = stageRepo
                .findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(stage.getRoadmapId()).stream()
                .filter(other -> other.getStatus() == RoadmapStageStatus.PASSED)
                .filter(other -> other.getOrderNo() < stage.getOrderNo()).reduce((first, second) -> second)
                .flatMap(previous -> milestoneRepo.findByStageId(previous.getId()))
                .map(RoadmapMilestone::getAchievedAt);
        return previousPass.filter(passed -> passed.isAfter(opened)).orElse(opened);
    }

    @Override
    public void settle(MilestoneContext context) {
        LearningRoadmap roadmap = context.roadmap();
        RoadmapMilestone milestone = context.milestone();
        if (milestone.getStatus() == MilestoneStatus.FAILED) {
            planner.tryRequestAdjustment(roadmap, AdjustmentReason.MILESTONE_FAILED);
            return;
        }
        if (milestone.getStatus() != MilestoneStatus.PASSED) {
            return;
        }
        // Any replan in flight started from the stage just passed; let it go rather than apply it behind the learner.
        roadmap.cancelAdjustment();
        RoadmapStage stage = context.stage();
        stage.pass(milestone);
        // Flushed before the next stage opens: only one stage may be current at a time, and Hibernate does not
        // promise to write two updates in the order they were made.
        stageRepo.saveAndFlush(stage);
        Optional<RoadmapStage> next = stageRepo.findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(roadmap.getId()).stream()
                .filter(candidate -> candidate.getStatus() == RoadmapStageStatus.LOCKED)
                .filter(candidate -> candidate.getOrderNo() > stage.getOrderNo()).findFirst();
        String before = "[{\"orderNo\":%d,\"status\":\"CURRENT\"}]".formatted(stage.getOrderNo());
        if (next.isPresent()) {
            next.get().open();
            adjustmentRepo
                    .save(RoadmapAdjustment.of(UUID.randomUUID(), roadmap.getId(), AdjustmentReason.MILESTONE_PASSED,
                            before, "[{\"orderNo\":%d,\"status\":\"PASSED\"},{\"orderNo\":%d,\"status\":\"CURRENT\"}]"
                                    .formatted(stage.getOrderNo(), next.get().getOrderNo())));
        } else {
            roadmap.complete(milestone, clock.instant());
            adjustmentRepo
                    .save(RoadmapAdjustment.of(UUID.randomUUID(), roadmap.getId(), AdjustmentReason.MILESTONE_PASSED,
                            before, "[{\"orderNo\":%d,\"status\":\"PASSED\"}]".formatted(stage.getOrderNo())));
        }
    }

    private static NotFoundException notFound(UUID milestoneId) {
        return new NotFoundException("MILESTONE_NOT_FOUND", "No milestone %s on your roadmap".formatted(milestoneId));
    }
}
