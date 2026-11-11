package com.englow3.exam.service.impl;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.exam.service.*;
import com.englow3.exam.repository.*;
import com.englow3.exam.entity.*;
import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.result.*;
import com.englow3.assessment.api.FullTestAssessmentStarter;
import com.englow3.user.api.UserDirectory;
import com.englow3.shared.error.*;
import lombok.RequiredArgsConstructor;

/** Exam is the sole owner of component bindings. No inference from unrelated practice attempts. */
@Service
@RequiredArgsConstructor
public class FullTestComponentServiceImpl implements FullTestComponentService {
    private final FullTestSittingRepository sittingRepo;
    private final FullTestRepository fullTestRepo;
    private final FullTestComponentAttemptRepository bindingRepo;
    private final ExamAttemptRepository attemptRepo;
    private final ExamAttemptService attempts;
    private final FullTestAssessmentStarter assessments;
    private final UserDirectory users;
    private final Clock clock;

    @Transactional
    public ExamAttemptResult startPaper(UUID id, StartExamAttemptCommand.OpenAttempt onOpen) {
        var sitting = ownedOpen(id);
        var test = fullTestRepo.findById(sitting.getFullTestId()).orElseThrow();
        var binding = binding(id, "PAPER");
        if (binding.getAttemptId() == null && sitting.getExamAttemptId() != null)
            binding.bind(sitting.getExamAttemptId());
        if (binding.getAttemptId() != null) {
            var existing = attemptRepo.findById(binding.getAttemptId()).orElseThrow();
            if (existing.getStatus() == ExamAttemptStatus.IN_PROGRESS
                    && clock.instant().isBefore(existing.getExpiresAt()))
                return ExamAttemptResult.started(existing, true);
            return attempts.result(existing.getId());
        }
        var active = attemptRepo.findFirstByUserIdAndExamIdAndModeAndStatusOrderByStartedAtDesc(sitting.getUserId(),
                test.getExamId(), ExamAttemptMode.FULL, ExamAttemptStatus.IN_PROGRESS);
        if (active.isPresent() && onOpen != StartExamAttemptCommand.OpenAttempt.REPLACE)
            throw new ConflictException("ATTEMPT_IN_PROGRESS",
                    "Finish the open paper before starting a new full-test paper");
        var started = attempts.start(new StartExamAttemptCommand(test.getExamId(), ExamAttemptMode.FULL, Set.of(), null,
                StartExamAttemptCommand.OpenAttempt.REPLACE));
        binding.bind(started.id());
        bindingRepo.saveAndFlush(binding);
        return started;
    }

    @Transactional
    public FullTestPartStartResult startAssessment(UUID id, FullTest.Slot slot, UUID expectedTaskId, String type,
            Long length) {
        var sitting = ownedOpen(id);
        var test = fullTestRepo.findById(sitting.getFullTestId()).orElseThrow();
        if (!test.taskId(slot).equals(expectedTaskId))
            throw new ConflictException("FULL_TEST_TASK_MISMATCH", "Open the task assigned to this full-test part");
        var binding = binding(id, slot.name());
        if (binding.getAttemptId() == null && sitting.partAttemptIds().get(slot.ordinal()) != null)
            binding.bind(sitting.partAttemptIds().get(slot.ordinal()));
        var prepared = assessments.start(test.taskId(slot), binding.getId(), type, length, binding.getAttemptId());
        binding.bind(prepared.attemptId());
        bindingRepo.saveAndFlush(binding);
        return new FullTestPartStartResult(prepared.attemptId(), prepared.uploadUrl());
    }

    private FullTestSitting ownedOpen(UUID id) {
        UUID user = users.requireCurrentUserId();
        var sitting = sittingRepo.findByIdForUpdate(id).filter(s -> s.getUserId().equals(user))
                .orElseThrow(() -> new NotFoundException("FULL_TEST_SITTING_NOT_FOUND", "Full test sitting not found"));
        if (sitting.getStatus() != FullTestSittingStatus.IN_PROGRESS
                || !clock.instant().isBefore(sitting.getDeadlineAt()))
            throw new ConflictException("FULL_TEST_CLOSED", "This sitting no longer accepts new parts");
        return sitting;
    }

    private FullTestComponentAttempt binding(UUID sitting, String slot) {
        return bindingRepo.findBySittingIdAndSlot(sitting, slot)
                .orElseGet(() -> bindingRepo.save(FullTestComponentAttempt.reserve(sitting, slot)));
    }
}
