package com.englow3.exam.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.AssessmentBandReader;
import com.englow3.exam.dto.command.SaveFullTestCommand;
import com.englow3.exam.dto.result.FullTestResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.helper.BandConversion;
import com.englow3.exam.query.ExamGradingQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.FullTestRepository;
import com.englow3.exam.repository.ScoreConversionRepository;
import com.englow3.exam.service.FullTestService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FullTestServiceImpl implements FullTestService {

    private final FullTestRepository fullTestRepo;
    private final ExamRepository examRepo;
    private final ExamSectionRepository examSectionRepo;
    private final AssessmentBandReader assessments;
    private final UserDirectory userDirectory;
    private final Clock clock;
    private final ExamGradingQuery grading;
    private final ScoreConversionRepository conversionRepo;

    @Override
    @Transactional
    public FullTestResult save(SaveFullTestCommand command) {
        if (examRepo.findById(command.examId()).isEmpty()) {
            throw new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(command.examId()));
        }
        if (command.id() == null) {
            return FullTestResult.of(fullTestRepo
                    .save(FullTest.draft(command.title(), command.description(), command.certificateVariant(),
                            command.examId(), command.taskIds(), userDirectory.requireCurrentUserId())));
        }
        FullTest test = require(command.id());
        test.update(command.title(), command.description(), command.certificateVariant(), command.examId(),
                command.taskIds());
        return FullTestResult.of(fullTestRepo.saveAndFlush(test));
    }

    @Override
    @Transactional(readOnly = true)
    public FullTestResult detail(UUID id) {
        return FullTestResult.of(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FullTestResult> list() {
        return fullTestRepo.findAllByOrderByCreatedAtDesc().stream().map(FullTestResult::of).toList();
    }

    @Override
    @Transactional
    public FullTestResult submitForReview(UUID id) {
        fullTestRepo.lockAvailability();
        FullTest test = require(id);
        test.submitForReview(paperFacts(test), taskFacts(test), clock.instant());
        return FullTestResult.of(test);
    }

    @Override
    @Transactional
    public FullTestResult approve(UUID id) {
        fullTestRepo.lockAvailability();
        FullTest test = require(id);
        test.approve(userDirectory.requireCurrentUserId(), paperFacts(test), taskFacts(test), clock.instant());
        return FullTestResult.of(test);
    }

    @Override
    @Transactional
    public FullTestResult reject(UUID id, String note) {
        FullTest test = require(id);
        test.reject(userDirectory.requireCurrentUserId(), note, clock.instant());
        return FullTestResult.of(test);
    }

    @Override
    @Transactional
    public FullTestResult archive(UUID id) {
        fullTestRepo.lockAvailability();
        FullTest test = require(id);
        test.archive();
        return FullTestResult.of(test);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FullTestResult> catalogue() {
        return fullTestRepo.findByStatusOrderByPublishedAtDesc(ExamStatus.PUBLISHED).stream().map(FullTestResult::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAvailable(UUID id) {
        FullTest test = require(id);
        test.requireSittable(paperFacts(test), taskFacts(test));
    }

    private FullTest.PaperFacts paperFacts(FullTest test) {
        return examRepo.findById(test.getExamId()).map(exam -> {
            List<SectionType> sections = examSectionRepo.findSectionTypes(exam.getId());
            return new FullTest.PaperFacts(exam.getStatus(), exam.getExamType(), exam.getCertificateType(),
                    exam.getCertificateVariant(), sections.contains(SectionType.LISTENING),
                    sections.contains(SectionType.READING), bandReady(test));
        }).orElse(null);
    }

    private boolean bandReady(FullTest test) {
        var questions = grading.load(test.getExamId());
        var conversions = conversionRepo.findByCertificateType(CertificateType.IELTS);
        var day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (var type : List.of(SectionType.LISTENING, SectionType.READING)) {
            var skillQuestions = questions.stream().filter(q -> q.sectionType() == type).toList();
            var max = skillQuestions.stream().map(ExamGradingQuery.GradingQuestion::maxRawScore).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            if (max.compareTo(BigDecimal.valueOf(40)) != 0 || skillQuestions.stream()
                    .anyMatch(q -> q.maxRawScore().compareTo(BigDecimal.valueOf(q.key().numberSpan())) != 0)) {
                return false;
            }
            for (int raw = 0; raw <= 40; raw++) {
                if (BandConversion
                        .convert(conversions, test.getCertificateVariant(), type, BigDecimal.valueOf(raw), max, day)
                        .isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<FullTest.TaskFacts> taskFacts(FullTest test) {
        return java.util.Arrays.stream(FullTest.Slot.values())
                .map(slot -> assessments.task(test.taskId(slot))
                        .map(task -> new FullTest.TaskFacts(slot, true, task.skill(), task.taskType(),
                                task.certificateType(), task.published(), task.hasImage()))
                        .orElse(new FullTest.TaskFacts(slot, false, null, null, null, false, false)))
                .toList();
    }

    private FullTest require(UUID id) {
        return fullTestRepo.findById(id).orElseThrow(
                () -> new NotFoundException("FULL_TEST_NOT_FOUND", "No full test with id %s".formatted(id)));
    }
}
