package com.englow3.assessment.service.impl;

import org.springframework.context.ApplicationEventPublisher;
import com.englow3.assessment.api.AssessmentAnswerGraded;
import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.assessment.helper.AssessmentRubric;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.service.AssessmentResultWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssessmentResultWriterImpl implements AssessmentResultWriter {
    private final AssessmentAttemptRepository attemptRepo;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AiJobQueue queue;
    private final ApplicationEventPublisher events;

    @Transactional
    public void complete(UUID id, int revision, String report, String transcript) {
        attemptRepo.lockById(id).ifPresent(a -> {
            if (a.finishAi(revision, AssessmentRubric.validate(mapper, a.getSkill(), report), transcript,
                    clock.instant())) {
                events.publishEvent(
                        new AssessmentAnswerGraded(a.getId(), a.getUserId(), a.getTaskId(), a.getSkill().name()));
            }
        });
    }

    @Transactional
    public void fail(UUID id, String code) {
        attemptRepo.lockById(id).ifPresent(a -> {
            if (queue.hasFailedProductiveAssessment(id, a.getGradingRevision())) {
                a.fail(code);
            }
        });
    }
}
