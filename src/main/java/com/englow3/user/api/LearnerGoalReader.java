package com.englow3.user.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A learner's goal and starting point, for the learning roadmap. Plain values only: the user module's entities and
 * enums stay inside it.
 */
public interface LearnerGoalReader {

    /** Empty when the user has no learner profile yet. */
    Optional<Goal> read(UUID userId);

    /**
     * @param currentLevel
     *            CEFR band, null until declared or measured
     * @param certificateLearner
     *            the learner chose the certificate purpose, so the goal is a score rather than a level
     * @param targetCertificate
     *            IELTS or TOEIC, null for a level goal
     * @param targetLevel
     *            CEFR band, null for a certificate goal
     * @param purposeCodes
     *            the learning purposes chosen, by code
     * @param targetSkills
     *            the skills the learner asked to focus on
     */
    record Goal(UUID userId, String currentLevel, boolean certificateLearner, String targetCertificate,
            BigDecimal currentScore, BigDecimal targetScore, String targetLevel, LocalDate targetDate,
            Integer dailyStudyMinutes, List<String> purposeCodes, List<String> targetSkills) {
    }
}
