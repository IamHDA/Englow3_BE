package com.englow3.exam.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.AssessmentBandReader;
import com.englow3.exam.api.FullTestCompleted;
import com.englow3.exam.dto.result.FullTestSittingResult;
import com.englow3.exam.entity.AttemptSectionResult;
import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.FullTestPartGrading;
import com.englow3.exam.entity.FullTestSitting;
import com.englow3.exam.entity.FullTestSittingStatus;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.helper.IeltsBand;
import com.englow3.exam.repository.AttemptSectionResultRepository;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.FullTestRepository;
import com.englow3.exam.repository.FullTestSittingRepository;
import com.englow3.exam.service.FullTestService;
import com.englow3.exam.service.FullTestSittingService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.PlacementRecorder;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Ties a learner's Listening + Reading sitting and five Writing and Speaking answers into one full test. The parts are
 * taken on their own screens; this service only finds them - the latest full scored sitting of the paper and the latest
 * submitted answer to each task, started since the full test was - and lets the sitting entity decide what counts. It
 * runs on every read, after every scored sitting or graded answer, and from the sweep, so a lost announcement only
 * delays a band.
 */
@Service
@RequiredArgsConstructor
public class FullTestSittingServiceImpl implements FullTestSittingService {

    private static final EnumSet<FullTestSittingStatus> UNSETTLED = EnumSet.of(FullTestSittingStatus.IN_PROGRESS,
            FullTestSittingStatus.AWAITING_GRADES);

    private final FullTestRepository fullTestRepo;
    private final FullTestSittingRepository sittingRepo;
    private final ExamAttemptRepository attemptRepo;
    private final AttemptSectionResultRepository sectionResultRepo;
    private final AssessmentBandReader assessments;
    private final LearnerGoalReader goals;
    private final PlacementRecorder placements;
    private final UserDirectory userDirectory;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final FullTestService fullTests;

    @Override
    @Transactional
    public FullTestSittingResult start(UUID fullTestId) {
        fullTestRepo.lockAvailability();
        UUID userId = userDirectory.requireCurrentUserId();
        FullTest test = fullTestRepo.findById(fullTestId).filter(found -> found.getStatus() == ExamStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("FULL_TEST_NOT_FOUND",
                        "No published full test with id %s".formatted(fullTestId)));
        boolean ieltsLearner = goals.read(userId)
                .map(goal -> goal.certificateLearner() && "IELTS".equals(goal.targetCertificate())).orElse(false);
        if (!ieltsLearner) {
            throw new ConflictException("FULL_TEST_IELTS_ONLY",
                    "The IELTS full test is for learners preparing for IELTS");
        }
        fullTests.requireAvailable(fullTestId);
        Optional<FullTestSitting> open = sittingRepo.findFirstByUserIdAndFullTestIdAndStatus(userId, fullTestId,
                FullTestSittingStatus.IN_PROGRESS);
        if (open.isPresent()) {
            FullTestSitting sitting = lock(open.get().getId());
            settle(sitting, test);
            if (sitting.getStatus() == FullTestSittingStatus.IN_PROGRESS) {
                return FullTestSittingResult.of(sitting, test, grading(sitting));
            }
            sittingRepo.flush();
        }
        return FullTestSittingResult.of(sittingRepo.save(FullTestSitting.start(fullTestId, userId, clock.instant())),
                test);
    }

    @Override
    @Transactional
    public FullTestSittingResult view(UUID sittingId) {
        UUID userId = userDirectory.requireCurrentUserId();
        FullTestSitting sitting = sittingRepo.findByIdForUpdate(sittingId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("FULL_TEST_SITTING_NOT_FOUND",
                        "No full test sitting with id %s".formatted(sittingId)));
        FullTest test = fullTestRepo.findById(sitting.getFullTestId()).orElseThrow();
        settle(sitting, test);
        return FullTestSittingResult.of(sitting, test, grading(sitting));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FullTestSittingResult> mine() {
        UUID userId = userDirectory.requireCurrentUserId();
        return sittingRepo.findByUserIdOrderByStartedAtDesc(userId).stream().map(sitting -> FullTestSittingResult
                .of(sitting, fullTestRepo.findById(sitting.getFullTestId()).orElseThrow())).toList();
    }

    /** Its own transaction: it runs after the grade that triggered it has committed, and per sitting in the sweep. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(UUID sittingId) {
        sittingRepo.findByIdForUpdate(sittingId)
                .ifPresent(sitting -> settle(sitting, fullTestRepo.findById(sitting.getFullTestId()).orElseThrow()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshFor(UUID userId) {
        for (FullTestSitting candidate : sittingRepo.findByUserIdAndStatusIn(userId, UNSETTLED)) {
            sittingRepo.findByIdForUpdate(candidate.getId()).ifPresent(
                    sitting -> settle(sitting, fullTestRepo.findById(sitting.getFullTestId()).orElseThrow()));
        }
    }

    /** How grading stands for each attached answer, so the screen can say which part to resend. */
    private List<FullTestPartGrading> grading(FullTestSitting sitting) {
        List<FullTestPartGrading> states = new ArrayList<>();
        for (UUID attemptId : sitting.partAttemptIds()) {
            states.add(attemptId == null ? null
                    : assessments.read(attemptId, sitting.getUserId())
                            .map(answer -> FullTestPartGrading.of(answer.status())).orElse(null));
        }
        return states;
    }

    private FullTestSitting lock(UUID id) {
        return sittingRepo.findByIdForUpdate(id).orElseThrow();
    }

    private void settle(FullTestSitting sitting, FullTest test) {
        if (sitting.isSettled()) {
            return;
        }
        Instant now = clock.instant();
        FullTestSitting.Change change = sitting.settle(progress(sitting, test), now);
        if (change.completed()) {
            placements.recordIeltsBand(sitting.getUserId(), sitting.getExamAttemptId(),
                    IeltsBand.cefr(sitting.getOverallBand()), sitting.getOverallBand());
            events.publishEvent(new FullTestCompleted(sitting.getId(), sitting.getUserId(), test.getId(),
                    sitting.getOverallBand()));
        } else if (change.provisionalSet()) {
            placements.recordIeltsBand(sitting.getUserId(), sitting.getExamAttemptId(),
                    IeltsBand.cefr(sitting.getProvisionalBand()), sitting.getProvisionalBand());
        }
    }

    private FullTestSitting.Progress progress(FullTestSitting sitting, FullTest test) {
        UUID userId = sitting.getUserId();
        Optional<ExamAttempt> paper = sitting.getExamAttemptId() != null
                ? attemptRepo.findById(sitting.getExamAttemptId())
                : attemptRepo
                        .findFirstByUserIdAndExamIdAndModeAndStatusAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
                                userId, test.getExamId(), ExamAttemptMode.FULL, ExamAttemptStatus.SCORED,
                                sitting.getStartedAt());
        BigDecimal listening = null;
        BigDecimal reading = null;
        if (paper.isPresent()) {
            for (AttemptSectionResult section : sectionResultRepo.findByExamAttemptId(paper.get().getId())) {
                if (section.getSectionType() == SectionType.LISTENING) {
                    listening = section.getConvertedScore();
                } else if (section.getSectionType() == SectionType.READING) {
                    reading = section.getConvertedScore();
                }
            }
        }
        List<FullTestSitting.Part> parts = new ArrayList<>();
        List<UUID> attached = sitting.partAttemptIds();
        for (FullTest.Slot slot : FullTest.Slot.values()) {
            UUID attemptId = attached.get(slot.ordinal());
            Optional<AssessmentBandReader.Answer> answer = attemptId != null ? assessments.read(attemptId, userId)
                    : assessments.latestAnswer(test.taskId(slot), userId, sitting.getStartedAt());
            parts.add(answer.map(found -> new FullTestSitting.Part(found.attemptId(), found.submittedAt(), found.band(),
                    found.assessedAt())).orElse(null));
        }
        Instant paperSubmitted = paper
                .map(attempt -> attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : attempt.getScoredAt())
                .orElse(null);
        return new FullTestSitting.Progress(paper.map(ExamAttempt::getId).orElse(null), paperSubmitted, listening,
                reading, parts);
    }
}
