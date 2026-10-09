package com.englow3.assessment.service.impl;

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

    @Transactional
    public void complete(UUID id, int revision, String report, String transcript) {
        attemptRepo.lockById(id).ifPresent(a -> a.finishAi(revision,
                AssessmentRubric.validate(mapper, a.getSkill(), report), transcript, clock.instant()));
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
