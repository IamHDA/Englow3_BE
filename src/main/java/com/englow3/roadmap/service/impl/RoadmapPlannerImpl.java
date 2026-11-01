package com.englow3.roadmap.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.MilestoneStatus;
import com.englow3.roadmap.entity.RoadmapGoalType;
import com.englow3.roadmap.entity.RoadmapMilestone;
import com.englow3.roadmap.entity.RoadmapStage;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;
import com.englow3.roadmap.helper.RoadmapPlan.Candidate;
import com.englow3.roadmap.helper.RoadmapPlan.Context;
import com.englow3.roadmap.helper.RoadmapPlan.MockPaper;
import com.englow3.roadmap.helper.RoadmapPrompt;
import com.englow3.roadmap.query.ContentCatalogQuery;
import com.englow3.roadmap.repository.RoadmapMilestoneRepository;
import com.englow3.roadmap.repository.RoadmapStageRepository;
import com.englow3.roadmap.service.RoadmapPlanner;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.user.api.LearnerGoalReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoadmapPlannerImpl implements RoadmapPlanner {

    /** How many pieces of each kind the AI chooses from. Enough for a varied plan; small enough for one prompt. */
    static final int CANDIDATES_PER_KIND = 25;
    /** Learners who did not say how long they study get a modest half hour a day. */
    static final int DEFAULT_DAILY_MINUTES = 30;

    private final RoadmapStageRepository stageRepo;
    private final RoadmapMilestoneRepository milestoneRepo;
    private final ContentCatalogQuery catalog;
    private final LearnerGoalReader goalReader;
    private final AiJobQueue queue;
    private final StudyCalendar calendar;
    private final ObjectMapper mapper;

    @Override
    public void requestFirstPlan(LearningRoadmap roadmap) {
        if (roadmap.getStatus() != RoadmapStatus.GENERATING || roadmap.getPendingJobToken() == null) {
            throw new ConflictException("ROADMAP_NOT_GENERATING", "This roadmap is not waiting for a first plan");
        }
        String payload = payload(roadmap, roadmap.getPendingJobToken(), null)
                .orElseThrow(() -> new ConflictException("ROADMAP_NO_CONTENT",
                        "There is not enough published content to plan this goal yet"));
        enqueue(roadmap, roadmap.getPendingJobToken(), payload);
    }

    @Override
    public void requestAdjustment(LearningRoadmap roadmap, AdjustmentReason reason) {
        UUID token = UUID.randomUUID();
        String payload = payload(roadmap, token, reason).orElseThrow(() -> new ConflictException("ROADMAP_NO_CONTENT",
                "There is not enough published content to plan this goal yet"));
        roadmap.requestAdjustment(reason, token);
        enqueue(roadmap, token, payload);
    }

    @Override
    public boolean tryRequestAdjustment(LearningRoadmap roadmap, AdjustmentReason reason) {
        if (roadmap.getStatus() != RoadmapStatus.ACTIVE || roadmap.isAdjusting()
                || !queue.hasRoadmapAllowance(roadmap.getUserId())) {
            return false;
        }
        UUID token = UUID.randomUUID();
        Optional<String> payload = payload(roadmap, token, reason);
        if (payload.isEmpty()) {
            return false;
        }
        roadmap.requestAdjustment(reason, token);
        enqueue(roadmap, token, payload.get());
        return true;
    }

    private void enqueue(LearningRoadmap roadmap, UUID token, String payload) {
        queue.enqueueRoadmapGeneration(roadmap.getId(), payload, "roadmap:%s:%s".formatted(roadmap.getId(), token),
                RoadmapPrompt.VERSION, roadmap.getUserId());
    }

    /** Empty when there is nothing to plan with: no content at these levels, or no mock paper for a certificate. */
    private Optional<String> payload(LearningRoadmap roadmap, UUID token, AdjustmentReason reason) {
        boolean certificate = roadmap.getGoalType() == RoadmapGoalType.CERTIFICATE;
        List<RoadmapStage> passed = reason == null ? List.of()
                : stageRepo.findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(roadmap.getId()).stream()
                        .filter(stage -> stage.getStatus() == RoadmapStageStatus.PASSED).toList();
        Map<UUID, RoadmapMilestone> milestones = reason == null ? Map.of()
                : milestoneRepo
                        .findByStageIdIn(stageRepo.findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(roadmap.getId())
                                .stream().map(RoadmapStage::getId).toList())
                        .stream().collect(Collectors.toMap(RoadmapMilestone::getStageId, Function.identity()));

        CefrLevel start = passed.isEmpty() ? roadmap.getStartLevel() : passed.getLast().getToLevel();
        CefrLevel top = certificate ? CefrLevel.ofRank(start.rank() + 3) : roadmap.getTargetLevel();
        CefrLevel bottom = CefrLevel.ofRank(start.rank() - 1);
        BigDecimal minimum = passed.stream().map(stage -> milestones.get(stage.getId())).filter(Objects::nonNull)
                .map(RoadmapMilestone::getRequiredScore).filter(Objects::nonNull).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        List<Candidate> candidates = catalog.candidates(bottom, top, roadmap.getTargetCertificate(),
                CANDIDATES_PER_KIND);
        List<MockPaper> papers = certificate ? catalog.mockPapers(roadmap.getTargetCertificate()) : List.of();
        if (candidates.isEmpty() || (certificate && papers.isEmpty())) {
            return Optional.empty();
        }
        int dailyMinutes = roadmap.getDailyMinutes() == null ? DEFAULT_DAILY_MINUTES : roadmap.getDailyMinutes();
        Context context = new Context(roadmap.getGoalType(), start, roadmap.getTargetLevel(),
                roadmap.getTargetCertificate(), roadmap.getTargetScore(), minimum, calendar.today(),
                roadmap.getTargetDate(), dailyMinutes, passed.size() + 1, candidates, papers);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("roadmapId", roadmap.getId().toString());
        payload.put("token", token.toString());
        payload.put("mode", reason == null ? "GENERATE" : "ADJUST");
        payload.put("reason", reason == null ? null : reason.name());
        payload.set("context", mapper.valueToTree(context));
        ObjectNode learner = payload.putObject("learner");
        goalReader.read(roadmap.getUserId()).ifPresent(goal -> {
            learner.set("purposes", mapper.valueToTree(goal.purposeCodes()));
            learner.set("targetSkills", mapper.valueToTree(goal.targetSkills()));
        });
        var history = payload.putArray("history");
        stageRepo.findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(roadmap.getId()).stream().filter(RoadmapStage::isInForce)
                .forEach(stage -> {
                    ObjectNode entry = history.addObject();
                    entry.put("orderNo", stage.getOrderNo());
                    entry.put("title", stage.getTitle());
                    entry.put("fromLevel", stage.getFromLevel().name());
                    entry.put("toLevel", stage.getToLevel().name());
                    entry.put("status", stage.getStatus().name());
                    RoadmapMilestone milestone = milestones.get(stage.getId());
                    if (milestone != null && milestone.getStatus() != MilestoneStatus.PENDING) {
                        entry.put("milestoneStatus", milestone.getStatus().name());
                        entry.put("requiredScore", milestone.getRequiredScore());
                        entry.put("resultScore", milestone.getResultScore());
                        entry.put("requiredLevel", Objects.toString(milestone.getRequiredLevel(), null));
                        entry.put("resultLevel", Objects.toString(milestone.getResultLevel(), null));
                    }
                });
        try {
            return Optional.of(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Roadmap payload could not be written", impossible);
        }
    }
}
