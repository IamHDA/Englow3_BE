package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Read-only: authoring has no write path yet, content arrives by SQL seed. No setter, no factory, no rule - the only
 * rule about a section lives one level up, in {@code Exam.publish(...)}, which is where the scores have to add up.
 * {@code examId} is a plain UUID rather than a {@code @ManyToOne}: nothing navigates the graph, the paper query
 * descends by parent-id set instead, so an association would only buy lazy loading nobody asked for.
 */
@Entity
@Table(name = "exam_sections")
@Getter
public class ExamSection {

    @Id
    private UUID id;

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
}
