package com.englow3.assessment.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.assessment.api.SpeakingLevelReader;
import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeakingLevelReaderImpl implements SpeakingLevelReader {
    private final AssessmentAttemptRepository attemptRepo;
    private final JdbcClient jdbcClient;
    private final ObjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<SpeakingLevel> read(UUID attemptId, UUID ownerId) {
        return attemptRepo.findById(attemptId).filter(
                attempt -> attempt.getUserId().equals(ownerId) && attempt.getSkill() == AssessmentSkill.SPEAKING)
                .map(this::levelOf);
    }

    private SpeakingLevel levelOf(AssessmentAttempt attempt) {
        if (attempt.getStatus() != AssessmentAttemptStatus.COMPLETED) {
            return new SpeakingLevel(attempt.getId(), false, attempt.getStatus() == AssessmentAttemptStatus.FAILED,
                    null, null, null);
        }
        BigDecimal overall = overall(attempt.getReport());
        Instant gradedAt = attempt.getAssessedAt();
        return new SpeakingLevel(attempt.getId(), true, false, overall,
                overall == null ? null : levelFor(overall, gradedAt == null ? Instant.now() : gradedAt), gradedAt);
    }

    private BigDecimal overall(String report) {
        try {
            JsonNode value = report == null ? null : mapper.readTree(report).path("overall");
            return value != null && value.isNumber() ? value.decimalValue() : null;
        } catch (JsonProcessingException unreadable) {
            return null;
        }
    }

    /** The conversion in force when the answer was graded, so a later table does not rewrite an earlier result. */
    private String levelFor(BigDecimal overall, Instant gradedAt) {
        return jdbcClient.sql("""
                select cefr_level
                  from speaking_level_conversions
                 where min_overall <= :overall and effective_from <= :gradedAt
                 order by effective_from desc, min_overall desc
                 limit 1
                """).param("overall", overall).param("gradedAt", java.sql.Timestamp.from(gradedAt)).query(String.class)
                .optional()
                // Below the lowest row of the table in force: the lowest band there is.
                .orElse("A1");
    }
}
