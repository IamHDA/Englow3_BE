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
 * One numbered question. Read-only, see {@link ExamSection}. {@code metadata jsonb} is not mapped, for the same reason
 * as on {@link SectionPart}. {@code questionCategory} stays a plain String: {@code varchar(40)}, nullable, and no
 * vocabulary has been decided for it - an enum would be inventing one.
 */
@Entity
@Table(name = "questions")
@Getter
public class Question {

    @Id
    private UUID id;

    @Column(name = "question_set_id", nullable = false)
    private UUID questionSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false)
    private DifficultyLevel difficultyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_type", nullable = false)
    private SkillType skillType;

    @Column(name = "question_category")
    private String questionCategory;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "max_raw_score", nullable = false)
    private BigDecimal maxRawScore;

    /** Shown on the admin detail and in the printed paper; the sitting's own tree load leaves it out. */
    private String explanation;

    protected Question() {
    }
}
