package com.englow3.roadmap.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.roadmap.entity.LevelCheckStatus;
import com.englow3.roadmap.entity.RoadmapStatus;
import com.englow3.roadmap.repository.LearningRoadmapRepository;
import com.englow3.roadmap.repository.LevelCheckRepository;
import com.englow3.roadmap.service.LevelCheckRefresher;
import com.englow3.roadmap.service.RoadmapProgression;
import com.englow3.roadmap.service.RoadmapResultSync;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoadmapResultSyncImpl implements RoadmapResultSync {

    /** A level check left unfinished longer than this is abandoned; the sweep stops looking at it. */
    static final Duration SWEEP_WINDOW = Duration.ofDays(14);

    private final LevelCheckRepository checkRepo;
    private final LearningRoadmapRepository roadmapRepo;
    private final LevelCheckRefresher refresher;
    private final RoadmapProgression progression;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void examSittingScored(UUID userId, UUID examId) {
        checkRepo.findByUserIdAndExamIdAndStatusNot(userId, examId, LevelCheckStatus.COMPLETED)
                .forEach(check -> refreshLocked(check.getId()));
        roadmapRepo.findOpen(userId).filter(roadmap -> roadmap.getStatus() == RoadmapStatus.ACTIVE)
                .ifPresent(progression::recordLatestMockSitting);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void speakingAnswerGraded(UUID userId, UUID taskId) {
        checkRepo.findByUserIdAndSpeakingTaskIdAndStatusNot(userId, taskId, LevelCheckStatus.COMPLETED)
                .forEach(check -> refreshLocked(check.getId()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshLevelCheck(UUID levelCheckId) {
        refreshLocked(levelCheckId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshRoadmap(UUID roadmapId) {
        roadmapRepo.findById(roadmapId).filter(roadmap -> roadmap.getStatus() == RoadmapStatus.ACTIVE)
                .ifPresent(progression::catchUp);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> unfinishedLevelCheckIds(int page, int size) {
        return checkRepo.findUnfinishedSince(clock.instant().minus(SWEEP_WINDOW), PageRequest.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeRoadmapIds(int page, int size) {
        return roadmapRepo.findActiveIds(PageRequest.of(page, size));
    }

    private void refreshLocked(UUID levelCheckId) {
        checkRepo.findByIdForUpdate(levelCheckId).filter(check -> check.getStatus() != LevelCheckStatus.COMPLETED)
                .ifPresent(refresher::refresh);
    }
}
