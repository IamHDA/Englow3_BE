package com.englow3.roadmap.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.roadmap.api.RoadmapTodayReader;
import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.MilestoneStatus;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapItem;
import com.englow3.roadmap.entity.RoadmapStage;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;
import com.englow3.roadmap.query.ItemProgressQuery;
import com.englow3.roadmap.repository.LearningRoadmapRepository;
import com.englow3.roadmap.repository.RoadmapItemRepository;
import com.englow3.roadmap.repository.RoadmapMilestoneRepository;
import com.englow3.roadmap.repository.RoadmapStageRepository;
import com.englow3.shared.time.StudyCalendar;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoadmapTodayReaderImpl implements RoadmapTodayReader {

    /** A day's list, not the stage's backlog. */
    static final int MAX_ITEMS = 6;

    private final LearningRoadmapRepository roadmapRepo;
    private final RoadmapStageRepository stageRepo;
    private final RoadmapItemRepository itemRepo;
    private final RoadmapMilestoneRepository milestoneRepo;
    private final ItemProgressQuery itemProgress;
    private final StudyCalendar calendar;

    @Override
    @Transactional(readOnly = true)
    public Optional<TodayPlan> today(UUID userId) {
        Optional<LearningRoadmap> roadmap = roadmapRepo.findOpen(userId)
                .filter(found -> found.getStatus() == RoadmapStatus.ACTIVE);
        if (roadmap.isEmpty()) {
            return Optional.empty();
        }
        Optional<RoadmapStage> current = stageRepo.findFirstByRoadmapIdAndStatus(roadmap.get().getId(),
                RoadmapStageStatus.CURRENT);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        RoadmapStage stage = current.get();
        List<RoadmapItem> items = itemRepo.findByStageIdOrderByOrderNoAsc(stage.getId());
        Map<RoadmapContentKind, List<UUID>> byKind = new EnumMap<>(RoadmapContentKind.class);
        items.forEach(item -> byKind.computeIfAbsent(item.getContentKind(), kind -> new ArrayList<>())
                .add(item.getContentId()));
        Map<UUID, Integer> done = itemProgress.percentDone(userId, byKind);

        LocalDate today = calendar.today();
        // Weeks count from the stage's planned start; before it, the first week's work is what is due.
        int week = today.isBefore(stage.getPlannedStart()) ? 1
                : (int) (ChronoUnit.DAYS.between(stage.getPlannedStart(), today) / 7) + 1;
        List<RoadmapItem> unfinished = items.stream()
                .filter(item -> done.getOrDefault(item.getContentId(), 0) < item.getTargetPercent()).toList();
        boolean requiredDone = unfinished.stream().noneMatch(RoadmapItem::isRequired);
        List<TodayItem> due = unfinished.stream().filter(item -> item.getPlannedWeek() <= week)
                .sorted(Comparator.comparing((RoadmapItem item) -> !item.isRequired())
                        .thenComparingInt(RoadmapItem::getPlannedWeek).thenComparingInt(RoadmapItem::getOrderNo))
                .limit(MAX_ITEMS)
                .map(item -> new TodayItem(item.getContentKind().name(), item.getContentId(), item.getTitle(),
                        item.getSkill(), done.getOrDefault(item.getContentId(), 0), item.getTargetPercent()))
                .toList();
        TodayMilestone milestone = !requiredDone ? null
                : milestoneRepo.findByStageId(stage.getId())
                        .filter(found -> found.getStatus() != MilestoneStatus.PASSED)
                        .map(found -> new TodayMilestone(found.getId(), found.getKind().name(), found.getExamId()))
                        .orElse(null);
        return Optional.of(new TodayPlan(roadmap.get().getId(), stage.getTitle(), stage.getOrderNo(), due, milestone));
    }
}
