package com.englow3.roadmap.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.SpeakingLevelReader;
import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.roadmap.dto.result.LevelCheckResult;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LevelCheck;
import com.englow3.roadmap.entity.LevelCheckPurpose;
import com.englow3.roadmap.entity.LevelCheckStatus;
import com.englow3.roadmap.entity.MilestoneKind;
import com.englow3.roadmap.query.ContentCatalogQuery;
import com.englow3.roadmap.repository.LevelCheckRepository;
import com.englow3.roadmap.service.LevelCheckRefresher;
import com.englow3.roadmap.service.LevelCheckService;
import com.englow3.roadmap.service.RoadmapProgression;
import com.englow3.roadmap.service.RoadmapProgression.MilestoneContext;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LevelCheckServiceImpl implements LevelCheckService {

    private final LevelCheckRepository checkRepo;
    private final ContentCatalogQuery catalog;
    private final RoadmapProgression progression;
    private final ScoredExamAttemptReader sittings;
    private final SpeakingLevelReader speaking;
    private final LearnerGoalReader goalReader;
    private final LevelCheckRefresher refresher;
    private final UserDirectory userDirectory;

    @Override
    @Transactional
    public LevelCheckResult start(LevelCheckPurpose purpose, UUID milestoneId) {
        UUID userId = userDirectory.requireCurrentUserId();
        UUID roadmapId = null;
        if (purpose == LevelCheckPurpose.ONBOARDING) {
            Optional<LevelCheck> open = checkRepo.findOpenOnboarding(userId);
            if (open.isPresent()) {
                return refreshAndView(lock(open.get().getId(), userId));
            }
            if (goalReader.read(userId).map(LearnerGoalReader.Goal::certificateLearner).orElse(false)) {
                throw new BadRequestException("LEVEL_CHECK_NOT_FOR_CERTIFICATE",
                        "Certificate learners take the placement test instead");
            }
        } else {
            if (milestoneId == null) {
                throw new BadRequestException("LEVEL_CHECK_PURPOSE_INVALID", "Say which milestone this check is for");
            }
            MilestoneContext context = progression.locate(userId, milestoneId);
            if (context.milestone().getKind() != MilestoneKind.LEVEL_CHECK) {
                throw new BadRequestException("MILESTONE_KIND_MISMATCH", "This milestone is a mock paper");
            }
            Optional<LevelCheck> open = checkRepo.findFirstByUserIdAndMilestoneIdAndStatusNotOrderByCreatedAtDesc(
                    userId, milestoneId, LevelCheckStatus.COMPLETED);
            if (open.isPresent()) {
                return refreshAndView(lock(open.get().getId(), userId));
            }
            roadmapId = context.roadmap().getId();
        }
        UUID examId = catalog.levelCheckPaper(checkRepo.usedExamIds(userId)).orElseThrow(
                () -> new NotFoundException("LEVEL_CHECK_PAPER_NOT_FOUND", "No level test is published yet"));
        UUID speakingTaskId = catalog.levelCheckSpeakingTask(checkRepo.usedSpeakingTaskIds(userId)).orElse(null);
        LevelCheck check = checkRepo.save(LevelCheck.start(UUID.randomUUID(), userId, purpose, roadmapId,
                purpose == LevelCheckPurpose.MILESTONE ? milestoneId : null, examId, speakingTaskId));
        return view(check, false, null);
    }

    @Override
    @Transactional
    public LevelCheckResult get(UUID levelCheckId) {
        return refreshAndView(lock(levelCheckId, userDirectory.requireCurrentUserId()));
    }

    @Override
    @Transactional
    public Optional<LevelCheckResult> latest() {
        UUID userId = userDirectory.requireCurrentUserId();
        return checkRepo.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(found -> refreshAndView(lock(found.getId(), userId)));
    }

    @Override
    @Transactional
    public LevelCheckResult attachExamAttempt(UUID levelCheckId, UUID examAttemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        LevelCheck check = lock(levelCheckId, userId);
        var sitting = sittings.read(examAttemptId, userId)
                .orElseThrow(() -> new NotFoundException("EXAM_ATTEMPT_NOT_FOUND",
                        "No sitting %s of yours".formatted(examAttemptId)));
        if (!sitting.examId().equals(check.getExamId())) {
            throw new BadRequestException("LEVEL_CHECK_WRONG_PAPER", "That sitting is of a different paper");
        }
        check.attachExamAttempt(examAttemptId);
        return refreshAndView(check);
    }

    @Override
    @Transactional
    public LevelCheckResult attachSpeakingAttempt(UUID levelCheckId, UUID assessmentAttemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        LevelCheck check = lock(levelCheckId, userId);
        var answer = speaking.read(assessmentAttemptId, userId)
                .orElseThrow(() -> new NotFoundException("ASSESSMENT_ATTEMPT_NOT_FOUND",
                        "No spoken answer %s of yours".formatted(assessmentAttemptId)));
        if (!Objects.equals(answer.taskId(), check.getSpeakingTaskId())) {
            throw new BadRequestException("LEVEL_CHECK_WRONG_TASK", "That answer is to a different task");
        }
        check.attachSpeakingAttempt(assessmentAttemptId);
        return refreshAndView(check);
    }

    @Override
    @Transactional
    public LevelCheckResult skipSpeaking(UUID levelCheckId) {
        LevelCheck check = lock(levelCheckId, userDirectory.requireCurrentUserId());
        check.skipSpeaking();
        return refreshAndView(check);
    }

    private LevelCheck lock(UUID levelCheckId, UUID userId) {
        return checkRepo.findByIdForUpdate(levelCheckId).filter(check -> check.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("LEVEL_CHECK_NOT_FOUND",
                        "No level check %s of yours".formatted(levelCheckId)));
    }

    private LevelCheckResult refreshAndView(LevelCheck check) {
        LevelCheckRefresher.Outcome outcome = refresher.refresh(check);
        return view(check, outcome.speakingFailed(), outcome.provisionalLevel());
    }

    private static LevelCheckResult view(LevelCheck check, boolean speakingFailed, CefrLevel provisional) {
        return new LevelCheckResult(check.getId(), check.getPurpose(), check.getStatus(), check.getMilestoneId(),
                check.getExamId(), check.getSpeakingTaskId(), check.getExamAttemptId(), check.getSpeakingAttemptId(),
                check.isSpeakingSkipped(), speakingFailed, check.getVocabLevel(), check.getListeningLevel(),
                check.getSpeakingLevel(), check.getResultLevel(), provisional, check.getCreatedAt(),
                check.getCompletedAt());
    }
}
