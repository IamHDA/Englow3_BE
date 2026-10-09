package com.englow3.assessment.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.dto.result.AssessmentNotificationResult;
import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentResultRead;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.repository.AssessmentResultReadRepository;
import com.englow3.assessment.service.AssessmentNotificationService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssessmentNotificationServiceImpl implements AssessmentNotificationService {
    private final AssessmentAttemptRepository attemptRepo;
    private final AssessmentResultReadRepository readRepo;
    private final UserDirectory userDirectory;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AssessmentNotificationResult> unread(int page) {
        return attemptRepo.unreadResults(userDirectory.requireCurrentUserId(), PageRequest.of(Math.max(0, page), 12))
                .map(a -> {
                    try {
                        var task = mapper.readValue(a.getTaskSnapshot(), AssessmentTaskResult.class);
                        return new AssessmentNotificationResult(a.getId(), task.title(), a.getSkill().name(),
                                a.getVersion(), a.getAssessedAt());
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new IllegalStateException("Invalid stored task snapshot", e);
                    }
                });
    }

    @Transactional
    public void markRead(UUID id, long version) {
        UUID user = userDirectory.requireCurrentUserId();
        var attempt = attemptRepo.lockById(id).filter(a -> a.getUserId().equals(user))
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Result not found"));
        if (attempt.getStatus() != AssessmentAttemptStatus.COMPLETED || attempt.getVersion() != version) {
            throw new ConflictException("RESULT_CHANGED", "Read the current result version before marking it read");
        }
        readRepo.save(AssessmentResultRead.of(id, version, clock.instant()));
    }
}
