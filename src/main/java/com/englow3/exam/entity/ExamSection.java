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
 * No rule of its own - the only rule about a section lives one level up, in {@code Exam.publish(...)}, which is where
 * the scores have to add up. {@code examId} is a plain UUID rather than a {@code @ManyToOne}: nothing navigates the
 * graph, the paper query descends by parent-id set instead, so an association would only buy lazy loading nobody asked
 * for. {@code create(...)} assigns its own id the same way {@code Exam.draft(...)} does. There is still no setter:
 * content authoring always replaces the whole tree ({@code AdminExamContentWriter}), never edits one row in place.
 */
@Entity
@Table(name = "exam_sections")
@Getter
public class ExamSection extends BasePersistedEntity {

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false)
    private SectionType sectionType;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "max_raw_score", nullable = false)
    private BigDecimal maxRawScore;

    @Column(name = "is_scored_by_criteria", nullable = false)
    private boolean scoredByCriteria;

    /** Null means the section borrows the paper's own duration. */
    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    protected ExamSection() {
    }

    public static ExamSection create(UUID examId, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, Integer timeLimitSeconds) {
        ExamSection section = new ExamSection();
        section.id = UUID.randomUUID();
        section.examId = examId;
        section.sectionType = sectionType;
        section.orderNo = orderNo;
        section.maxRawScore = maxRawScore;
        section.scoredByCriteria = scoredByCriteria;
        section.timeLimitSeconds = timeLimitSeconds;
        return section;
    }
}
