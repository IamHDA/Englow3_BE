package com.englow3.roadmap.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.RoadmapAdjustment;
import com.englow3.roadmap.entity.RoadmapItem;
import com.englow3.roadmap.entity.RoadmapMilestone;
import com.englow3.roadmap.entity.RoadmapStage;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedItem;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedStage;
import com.englow3.roadmap.repository.LearningRoadmapRepository;
import com.englow3.roadmap.repository.RoadmapAdjustmentRepository;
import com.englow3.roadmap.repository.RoadmapItemRepository;
import com.englow3.roadmap.repository.RoadmapMilestoneRepository;
import com.englow3.roadmap.repository.RoadmapStageRepository;
import com.englow3.roadmap.service.RoadmapPlanWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoadmapPlanWriterImpl implements RoadmapPlanWriter {

    private final LearningRoadmapRepository roadmapRepo;
    private final RoadmapStageRepository stageRepo;
    private final RoadmapItemRepository itemRepo;
    private final RoadmapMilestoneRepository milestoneRepo;
    private final RoadmapAdjustmentRepository adjustmentRepo;
    private final ObjectMapper mapper;

    @Override
    @Transactional
    public void apply(UUID roadmapId, UUID token, List<ProposedStage> stages, String modelName, String promptVersion) {
        LearningRoadmap roadmap = roadmapRepo.findByIdForUpdate(roadmapId).orElse(null);
        if (roadmap == null || !roadmap.isWaitingFor(token)) {
            // Replaced, abandoned or deleted meanwhile: the plan is for a request nobody is waiting on.
            return;
        }
        List<RoadmapStage> existing = stageRepo.findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(roadmapId).stream()
                .filter(RoadmapStage::isInForce).toList();
        List<RoadmapStage> kept = existing.stream().filter(stage -> stage.getStatus() == RoadmapStageStatus.PASSED)
                .toList();
        List<RoadmapStage> replaced = existing.stream().filter(stage -> stage.getStatus() != RoadmapStageStatus.PASSED)
                .toList();

        String before = summary(replaced);
        replaced.forEach(RoadmapStage::replace);
        // Updates before inserts: the new stages take the same positions, and only stages in force must be unique.
        stageRepo.saveAllAndFlush(replaced);

        List<RoadmapStage> created = new ArrayList<>();
        int orderNo = kept.size();
        for (ProposedStage proposed : stages) {
            orderNo++;
            RoadmapStage stage = RoadmapStage.plan(UUID.randomUUID(), roadmapId, orderNo, proposed.title(),
                    proposed.summary(), proposed.fromLevel(), proposed.toLevel(),
                    String.join(",", proposed.focusSkills()), proposed.plannedStart(), proposed.plannedEnd(),
                    created.isEmpty());
            created.add(stage);
        }
        stageRepo.saveAllAndFlush(created);
        List<RoadmapItem> items = new ArrayList<>();
        List<RoadmapMilestone> milestones = new ArrayList<>();
        for (int i = 0; i < created.size(); i++) {
            RoadmapStage stage = created.get(i);
            ProposedStage proposed = stages.get(i);
            int itemNo = 0;
            for (ProposedItem item : proposed.items()) {
                items.add(RoadmapItem.of(UUID.randomUUID(), stage.getId(), ++itemNo, item.kind(), item.contentId(),
                        item.title(), item.skill(), item.required(), item.targetPercent(), item.plannedWeek(),
                        item.estimatedMinutes()));
            }
            milestones.add(proposed.milestone().fullTest()
                    ? RoadmapMilestone.mockFullTest(UUID.randomUUID(), stage.getId(), proposed.milestone().examId(),
                            proposed.milestone().requiredScore())
                    : proposed.milestone().examId() != null
                            ? RoadmapMilestone.mockExam(UUID.randomUUID(), stage.getId(), proposed.milestone().examId(),
                                    proposed.milestone().requiredScore())
                            : RoadmapMilestone.levelCheck(UUID.randomUUID(), stage.getId(),
                                    proposed.milestone().requiredLevel()));
        }
        itemRepo.saveAll(items);
        milestoneRepo.saveAll(milestones);

        if (roadmap.getStatus() == RoadmapStatus.GENERATING) {
            roadmap.activate(token, modelName, promptVersion);
        } else {
            adjustmentRepo.save(RoadmapAdjustment.of(UUID.randomUUID(), roadmapId, roadmap.getPendingReason(), before,
                    summary(created)));
            roadmap.applyAdjustment(token, modelName, promptVersion);
        }
    }

    @Override
    @Transactional
    public void giveUp(UUID roadmapId, String errorCode) {
        roadmapRepo.findByIdForUpdate(roadmapId)
                .ifPresent(roadmap -> roadmap.giveUp(roadmap.getPendingJobToken(), errorCode));
    }

    private String summary(List<RoadmapStage> stages) {
        ArrayNode array = mapper.createArrayNode();
        for (RoadmapStage stage : stages) {
            array.addObject().put("orderNo", stage.getOrderNo()).put("title", stage.getTitle())
                    .put("fromLevel", stage.getFromLevel().name()).put("toLevel", stage.getToLevel().name())
                    .put("plannedStart", stage.getPlannedStart().toString())
                    .put("plannedEnd", stage.getPlannedEnd().toString());
        }
        return array.toString();
    }
}
