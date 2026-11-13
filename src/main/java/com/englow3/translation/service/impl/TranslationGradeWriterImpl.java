package com.englow3.translation.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.translation.repository.TranslationAttemptRepository;
import com.englow3.translation.service.TranslationGradeWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TranslationGradeWriterImpl implements TranslationGradeWriter {

    private final TranslationAttemptRepository attemptRepo;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public void storeGrade(UUID attemptId, String report) {
        int score;
        try {
            score = mapper.readTree(report).path("score").asInt();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid translation report", e);
        }
        attemptRepo.findById(attemptId).ifPresent(attempt -> attempt.recordGrade(score, report, clock.instant()));
    }

    @Override
    @Transactional
    public void markFailed(UUID attemptId, String errorCode) {
        attemptRepo.findById(attemptId).ifPresent(attempt -> attempt.recordFailure(errorCode, clock.instant()));
    }
}
