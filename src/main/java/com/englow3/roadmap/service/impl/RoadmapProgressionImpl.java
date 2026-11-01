package com.englow3.roadmap.service.impl;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.roadmap.entity.LearningRoadmap;
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

import lombok.RequiredArgsConstructor;

/** Joins the caller's transaction; every method expects one to be open. */
@Service
@RequiredArgsConstructor
public class RoadmapProgressionImpl implements RoadmapProgression {

    private final LearningRoadmapRepository roadmapRepo;
    private final RoadmapStageRepository stageRepo;
    private final RoadmapMilestoneRepository milestoneRepo;
    private final RoadmapAdjustmentRepository adjustmentRepo;
    private final RoadmapPlanner planner;
    private final Clock clock;

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
