package com.englow3.assessment.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.AssessmentBandReader;
import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentTaskStatus;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.repository.AssessmentTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssessmentBandReaderImpl implements AssessmentBandReader {

    private final AssessmentAttemptRepository attemptRepo;
    private final AssessmentTaskRepository taskRepo;
    private final ObjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<Answer> read(UUID attemptId, UUID ownerId) {
        return attemptRepo.findById(attemptId).filter(attempt -> attempt.getUserId().equals(ownerId))
                .map(this::answerOf);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Answer> latestAnswer(UUID taskId, UUID ownerId, Instant since) {
        return attemptRepo.findFirstByUserIdAndTaskIdAndStatusNotAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                ownerId, taskId, AssessmentAttemptStatus.DRAFT, since).map(this::answerOf);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Task> task(UUID taskId) {
        return taskRepo.findById(taskId)
                .map(task -> new Task(task.getId(), task.getSkill().name(), task.getTaskType(),
                        task.getCertificateType(), task.getStatus() == AssessmentTaskStatus.PUBLISHED,
                        task.getImageObjectKey() != null));
    }

    private Answer answerOf(AssessmentAttempt attempt) {
        boolean completed = attempt.getStatus() == AssessmentAttemptStatus.COMPLETED;
        if (attempt.getStatus() == AssessmentAttemptStatus.DRAFT) {
            return new Answer(attempt.getId(), attempt.getTaskId(), null, false, null, attempt.getStatus().name(),
                    null);
        }
        Instant submittedAt = attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : attempt.getCreatedAt();
        return new Answer(attempt.getId(), attempt.getTaskId(), submittedAt, completed,
                completed ? overall(attempt.getReport()) : null, attempt.getStatus().name(), attempt.getAssessedAt());
    }

    private BigDecimal overall(String report) {
        try {
            JsonNode value = report == null ? null : mapper.readTree(report).path("overall");
            return value != null && value.isNumber() ? value.decimalValue() : null;
        } catch (JsonProcessingException unreadable) {
            return null;
        }
    }
}
