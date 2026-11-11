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
    private final com.englow3.exam.repository.FullTestComponentAttemptRepository bindingRepo;
    private final com.englow3.exam.repository.FullTestBandRevisionRepository revisionRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

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
        return mine(0, 20);
    }

    @Transactional(readOnly = true)
    public List<FullTestSittingResult> mine(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new com.englow3.shared.error.BadRequestException("PAGE_INVALID", "Choose a page size from 1 to 100");
        UUID userId = userDirectory.requireCurrentUserId();
        var rows = sittingRepo.findByUserIdOrderByStartedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(page, size));
        var tests = fullTestRepo.findAllById(rows.stream().map(FullTestSitting::getFullTestId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(FullTest::getId, java.util.function.Function.identity()));
        return rows.stream().map(s -> FullTestSittingResult.of(s, tests.get(s.getFullTestId()))).toList();
    }

    /** Its own transaction: it runs after the grade that triggered it has committed, and per sitting in the sweep. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(UUID sittingId) {
        sittingRepo.findByIdForUpdate(sittingId).ifPresent(sitting -> {
            settle(sitting, fullTestRepo.findById(sitting.getFullTestId()).orElseThrow());
            sitting.reconciledAt(clock.instant());
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshFor(UUID userId) {
        for (FullTestSitting candidate : sittingRepo.findRefreshableForUser(userId)) {
            sittingRepo.findByIdForUpdate(candidate.getId()).ifPresent(
                    sitting -> settle(sitting, fullTestRepo.findById(sitting.getFullTestId()).orElseThrow()));
        }
    }

    /** How grading stands for each attached answer, so the screen can say which part to resend. */
    private List<FullTestPartGrading> grading(FullTestSitting sitting) {
        var answers = assessments
                .readAll(sitting.partAttemptIds().stream().filter(java.util.Objects::nonNull).toList(),
                        sitting.getUserId())
                .stream().collect(java.util.stream.Collectors.toMap(AssessmentBandReader.Answer::attemptId,
                        java.util.function.Function.identity()));
        List<FullTestPartGrading> states = new ArrayList<>();
        for (UUID attemptId : sitting.partAttemptIds()) {
            states.add(attemptId == null || !answers.containsKey(attemptId) ? null
                    : FullTestPartGrading.of(answers.get(attemptId).status()));
        }
        return states;
    }

    private FullTestSitting lock(UUID id) {
        return sittingRepo.findByIdForUpdate(id).orElseThrow();
    }

    private void settle(FullTestSitting sitting, FullTest test) {
        if (sitting.getStatus() == FullTestSittingStatus.EXPIRED) {
            return;
        }
        Instant now = clock.instant();
        boolean wasCompleted = sitting.getStatus() == FullTestSittingStatus.COMPLETED;
        String previous = wasCompleted ? bandsJson(sitting) : null;
        FullTestSitting.Change change = sitting.settle(progress(sitting, test), now);
        if (change.completed()) {
            if (wasCompleted)
                revisionRepo.save(
                        com.englow3.exam.entity.FullTestBandRevision.of(sitting, previous, bandsJson(sitting), now));
            placements.recordIeltsBand(sitting.getUserId(), sitting.getExamAttemptId(),
                    IeltsBand.cefr(sitting.getOverallBand()), sitting.getOverallBand(), sitting.getStartedAt(), true);
            events.publishEvent(new FullTestCompleted(sitting.getId(), sitting.getUserId(), test.getId(),
                    sitting.getOverallBand()));
        } else if (change.provisionalSet()) {
            placements.recordIeltsBand(sitting.getUserId(), sitting.getExamAttemptId(),
                    IeltsBand.cefr(sitting.getProvisionalBand()), sitting.getProvisionalBand(), sitting.getStartedAt(),
                    false);
        }
    }

    private FullTestSitting.Progress progress(FullTestSitting sitting, FullTest test) {
        UUID userId = sitting.getUserId();
        var bindings = bindingRepo.findBySittingId(sitting.getId()).stream().filter(b -> b.getAttemptId() != null)
                .collect(java.util.stream.Collectors.toMap(com.englow3.exam.entity.FullTestComponentAttempt::getSlot,
                        com.englow3.exam.entity.FullTestComponentAttempt::getAttemptId));
        UUID paperId = sitting.getExamAttemptId() != null ? sitting.getExamAttemptId() : bindings.get("PAPER");
        Optional<ExamAttempt> paper = paperId == null ? Optional.empty()
                : attemptRepo.findById(paperId)
                        .filter(a -> a.getUserId().equals(userId) && a.getExamId().equals(test.getExamId())
                                && a.getMode() == ExamAttemptMode.FULL && a.getStatus() == ExamAttemptStatus.SCORED);
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
        var answerIds = new java.util.HashSet<>(bindings.values());
        answerIds.remove(bindings.get("PAPER"));
        attached.stream().filter(java.util.Objects::nonNull).forEach(answerIds::add);
        var answers = assessments.readAll(answerIds, userId).stream().collect(java.util.stream.Collectors
                .toMap(AssessmentBandReader.Answer::attemptId, java.util.function.Function.identity()));
        for (FullTest.Slot slot : FullTest.Slot.values()) {
            UUID attemptId = attached.get(slot.ordinal()) != null ? attached.get(slot.ordinal())
                    : bindings.get(slot.name());
            Optional<AssessmentBandReader.Answer> answer = attemptId == null ? Optional.empty()
                    : Optional.ofNullable(answers.get(attemptId)).filter(a -> a.taskId().equals(test.taskId(slot)));
            parts.add(answer.map(found -> new FullTestSitting.Part(found.attemptId(), found.submittedAt(), found.band(),
                    found.assessedAt(), found.version())).orElse(null));
        }
        Instant paperSubmitted = paper
                .map(attempt -> attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : attempt.getScoredAt())
                .orElse(null);
        return new FullTestSitting.Progress(paper.map(ExamAttempt::getId).orElse(null), paperSubmitted, listening,
                reading, parts);
    }

    private String bandsJson(FullTestSitting sitting) {
        try {
            return mapper.writeValueAsString(sitting.bandSnapshot());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode band audit", e);
        }
    }
}
