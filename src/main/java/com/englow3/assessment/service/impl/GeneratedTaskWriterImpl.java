package com.englow3.assessment.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.GeneratedTaskWriter;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.dto.request.AssessmentTaskRequest;
import com.englow3.assessment.entity.AssessmentTask;
import com.englow3.assessment.helper.TaskImages;
import com.englow3.assessment.repository.AssessmentTaskRepository;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeneratedTaskWriterImpl implements GeneratedTaskWriter {

    private final AssessmentTaskRepository taskRepo;
    private final TaskImages images;
    private final ObjectMapper mapper;
    private final Validator validator;

    @Override
    @Transactional
    public UUID createDraft(UUID authorId, String taskJson) {
        AssessmentTaskRequest request;
        try {
            request = mapper.readValue(taskJson, AssessmentTaskRequest.class);
        } catch (JsonProcessingException unreadable) {
            throw new BadRequestException("GENERATED_TASK_UNREADABLE", "The generated task is not valid JSON");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new BadRequestException("GENERATED_TASK_INVALID",
                    "%s %s".formatted(first.getPropertyPath(), first.getMessage()));
        }
        return taskRepo.saveAndFlush(AssessmentTask.draft(AssessmentTaskCommand.of(request), authorId)).getId();
    }

    @Override
    public String uploadImage(UUID taskId, byte[] png) {
        return images.upload(taskId, png, "image/png");
    }

    @Override
    @Transactional
    public void attachImage(UUID taskId, String objectKey) {
        AssessmentTask task = lock(taskId);
        task.attachImage(objectKey);
        taskRepo.saveAndFlush(task);
    }

    @Override
    @Transactional
    public void submitForReview(UUID taskId) {
        AssessmentTask task = lock(taskId);
        task.submit();
        taskRepo.saveAndFlush(task);
    }

    private AssessmentTask lock(UUID taskId) {
        return taskRepo.lockById(taskId).orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Task not found"));
    }
}
