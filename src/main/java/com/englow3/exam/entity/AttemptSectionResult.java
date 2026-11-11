package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * What one section of a scored attempt came to: its points and, where the certificate publishes a conversion for a
 * whole section, the band. Written once, when the attempt is scored.
 */
@Entity
@Table(name = "attempt_section_results")
@Getter
public class AttemptSectionResult extends BasePersistedEntity {

    @Column(name = "exam_attempt_id", nullable = false, updatable = false)
    private UUID examAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, updatable = false)
    private SectionType sectionType;

    @Column(name = "raw_score")
    private BigDecimal rawScore;

    @Column(name = "max_raw_score", nullable = false)
    private BigDecimal maxRawScore;

    /** The configured section score, or null when no complete conversion applies (including every practice). */
    @Column(name = "converted_score")
    private BigDecimal convertedScore;

    @Column(name = "correct_answer_count")
    private Integer correctAnswerCount;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    protected AttemptSectionResult() {
    }

    public static AttemptSectionResult of(UUID attemptId, SectionType sectionType, BigDecimal rawScore,
            BigDecimal maxRawScore, BigDecimal convertedScore, int correctAnswerCount, int questionCount) {
        AttemptSectionResult result = new AttemptSectionResult();
        result.id = UUID.randomUUID();
        result.examAttemptId = attemptId;
        result.sectionType = sectionType;
        result.rawScore = rawScore;
        result.maxRawScore = maxRawScore;
        result.convertedScore = convertedScore;
        result.correctAnswerCount = correctAnswerCount;
        result.questionCount = questionCount;
        return result;
    }
}
