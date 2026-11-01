package com.englow3.roadmap.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.SpeakingLevelReader;
import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.exam.api.ScoredExamAttemptReader.Sitting;
import com.englow3.roadmap.dto.result.LevelCheckResult;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LevelCheck;
import com.englow3.roadmap.entity.LevelCheckPurpose;
import com.englow3.roadmap.entity.LevelCheckStatus;
import com.englow3.roadmap.entity.MilestoneKind;
import com.englow3.roadmap.helper.LevelCombiner;
import com.englow3.roadmap.query.ContentCatalogQuery;
import com.englow3.roadmap.query.LevelBandQuery;
import com.englow3.roadmap.repository.LevelCheckRepository;
import com.englow3.roadmap.service.LevelCheckService;
import com.englow3.roadmap.service.RoadmapProgression;
import com.englow3.roadmap.service.RoadmapProgression.MilestoneContext;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.PlacementRecorder;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LevelCheckServiceImpl implements LevelCheckService {

    /** The paper's vocabulary and grammar questions sit in its READING section. */
    static final String VOCABULARY_SECTION = "READING";
    static final String LISTENING_SECTION = "LISTENING";

    private final LevelCheckRepository checkRepo;
    private final ContentCatalogQuery catalog;
    private final LevelBandQuery bands;
    private final RoadmapProgression progression;
    private final ScoredExamAttemptReader sittings;
    private final SpeakingLevelReader speaking;
    private final LearnerGoalReader goalReader;
    private final PlacementRecorder placementRecorder;
    private final UserDirectory userDirectory;
    private final Clock clock;

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
        Sitting sitting = sittings.read(examAttemptId, userId)
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

    /**
     * Reads each part's grade from the module that owns it, records what has arrived, and finishes the check once it
     * can. Done on every look rather than on an event, so a grade that lands while nobody is looking is picked up the
     * next time anyone does - and neither the exam nor the assessment module needs to know the roadmap exists.
     */
    private LevelCheckResult refreshAndView(LevelCheck check) {
        if (check.getStatus() == LevelCheckStatus.COMPLETED) {
            return view(check, false, null);
        }
        UUID userId = check.getUserId();
        if (check.getExamAttemptId() != null && !check.hasWrittenParts()) {
            sittings.read(check.getExamAttemptId(), userId).filter(sitting -> sitting.scored() && sitting.full())
                    .ifPresent(sitting -> check.recordWrittenParts(band(sitting, VOCABULARY_SECTION),
                            band(sitting, LISTENING_SECTION)));
        }
        boolean speakingFailed = false;
        if (check.getSpeakingAttemptId() != null && check.getSpeakingLevel() == null) {
            var answer = speaking.read(check.getSpeakingAttemptId(), userId).orElse(null);
            if (answer != null && answer.completed()) {
                CefrLevel.parse(answer.level()).ifPresent(check::recordSpeaking);
            }
            speakingFailed = answer != null && answer.failed();
        }
        if (!check.hasWrittenParts()) {
            return view(check, speakingFailed, null);
        }
        LevelCombiner.Weights weights = LevelCombiner
                .forPurposes(goalReader.read(userId).map(LearnerGoalReader.Goal::purposeCodes).orElse(List.of()));
        if (!check.isReadyToComplete()) {
            CefrLevel provisional = LevelCombiner
                    .combine(check.getVocabLevel(), check.getListeningLevel(), null, weights).level();
            return view(check, speakingFailed, provisional);
        }
        LevelCombiner.Result result = LevelCombiner.combine(check.getVocabLevel(), check.getListeningLevel(),
                check.getSpeakingLevel(), weights);
        check.complete(result.level(), result.appliedWeights(), clock.instant());
        // The learner's level is what they were last measured at, whichever test measured it.
        placementRecorder.recordLevelCheck(userId, check.getId(), result.level().name());
        if (check.getPurpose() == LevelCheckPurpose.MILESTONE) {
            progression.find(userId, check.getMilestoneId()).ifPresent(context -> {
                context.milestone().recordLevelCheck(check.getId(), result.level(), clock.instant());
                progression.settle(context);
            });
        }
        return view(check, false, null);
    }

    /** A section's share of its points as a band; a paper without that section is judged on its overall share. */
    private CefrLevel band(Sitting sitting, String section) {
        BigDecimal percent = sitting.sections().stream().filter(score -> section.equals(score.sectionType()))
                .filter(score -> score.max() != null && score.max().signum() > 0).findFirst()
                .map(score -> score.awarded().multiply(BigDecimal.valueOf(100)).divide(score.max(), 2,
                        RoundingMode.DOWN))
                .orElse(sitting.scorePercentage() == null ? BigDecimal.ZERO : sitting.scorePercentage());
        return bands.band(section, percent, sitting.scoredAt() == null ? clock.instant() : sitting.scoredAt())
                .orElse(CefrLevel.A1);
    }

    private static LevelCheckResult view(LevelCheck check, boolean speakingFailed, CefrLevel provisional) {
        return new LevelCheckResult(check.getId(), check.getPurpose().name(), check.getStatus().name(),
                check.getMilestoneId(), check.getExamId(), check.getSpeakingTaskId(), check.getExamAttemptId(),
                check.getSpeakingAttemptId(), check.isSpeakingSkipped(), speakingFailed, name(check.getVocabLevel()),
                name(check.getListeningLevel()), name(check.getSpeakingLevel()), name(check.getResultLevel()),
                name(provisional), check.getCreatedAt(), check.getCompletedAt());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
