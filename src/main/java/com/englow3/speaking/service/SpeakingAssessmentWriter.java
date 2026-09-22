package com.englow3.speaking.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingAttemptWord;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingAttemptWordRepository;
import com.englow3.speaking.service.SpeechAssessmentParser.Assessment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * The two short writes at the end of an assessment.
 * <p>
 * A separate bean from the handler, and that is the point rather than an accident of tidiness: {@code @Transactional}
 * works through a proxy, so a handler calling its own transactional method would run it with no transaction at all and
 * nothing would say so. Crossing a bean boundary is what makes the annotation mean anything.
 */
@Service
@RequiredArgsConstructor
public class SpeakingAssessmentWriter {

    private final SpeakingAttemptRepository attemptRepo;
    private final SpeakingAttemptWordRepository wordRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public void storeAssessment(UUID attemptId, Assessment assessment) {
        attemptRepo.findById(attemptId).ifPresent(attempt -> {
            attempt.recordAssessment(assessment.recognizedText(), assessment.accuracy(), assessment.fluency(),
                    assessment.completeness(), assessment.prosody(), assessment.pronunciation(), Instant.now());
            replaceWords(attempt, assessment);
        });
    }

    @Transactional
    public void markFailed(UUID attemptId, String errorCode) {
        attemptRepo.findById(attemptId).ifPresent(attempt -> attempt.recordFailure(errorCode, Instant.now()));
    }

    /**
     * Deletes before inserting. A job can run twice - a stall reclaim is exactly that - and appending would leave one
     * recording with two copies of its own word list.
     */
    private void replaceWords(SpeakingAttempt attempt, Assessment assessment) {
        wordRepo.deleteBySpeakingAttemptId(attempt.getId());

        List<SpeakingAttemptWord> rows = new ArrayList<>();
        int orderNo = 1;
        for (SpeechAssessmentParser.Word word : assessment.words()) {
            rows.add(SpeakingAttemptWord.of(attempt.getId(), orderNo++, word.word(), word.accuracy(), word.errorType(),
                    word.offsetMs(), word.durationMs(), phonemesJson(word)));
        }
        wordRepo.saveAll(rows);
    }

    private String phonemesJson(SpeechAssessmentParser.Word word) {
        try {
            return objectMapper.writeValueAsString(word.phonemes());
        } catch (JsonProcessingException impossible) {
            // A list of records holding a String and a BigDecimal; there is nothing here Jackson can refuse.
            throw new IllegalStateException("Could not serialise a phoneme breakdown", impossible);
        }
    }
}
