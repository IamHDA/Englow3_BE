package com.englow3.exam.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.AssessmentBandReader;
import com.englow3.exam.dto.command.SaveFullTestCommand;
import com.englow3.exam.dto.result.FullTestResult;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.FullTestRepository;
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
        FullTest test = require(id);
        test.submitForReview(paperFacts(test), taskFacts(test), clock.instant());
        return FullTestResult.of(test);
    }

    @Override
    @Transactional
    public FullTestResult approve(UUID id) {
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

    private FullTest.PaperFacts paperFacts(FullTest test) {
        return examRepo.findById(test.getExamId()).map(exam -> {
            List<SectionType> sections = examSectionRepo.findSectionTypes(exam.getId());
            return new FullTest.PaperFacts(exam.getStatus(), exam.getExamType(), exam.getCertificateType(),
                    exam.getCertificateVariant(), sections.contains(SectionType.LISTENING),
                    sections.contains(SectionType.READING));
        }).orElse(null);
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
