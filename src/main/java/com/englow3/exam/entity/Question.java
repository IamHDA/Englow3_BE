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
 * One numbered question. See {@link ExamSection} for why there is no association to its parent, no setter, and a
 * {@code create(...)} factory instead. {@code metadata jsonb} is not mapped, for the same reason as on
 * {@link SectionPart}. {@code questionCategory} stays a plain String: {@code varchar(40)}, nullable, and no vocabulary
 * has been decided for it - an enum would be inventing one. {@code sourceQuestionId} is set only when this row is a
 * copy made from the question bank (Phase 5); it names the original, never a new table.
 */
@Entity
@Table(name = "questions")
@Getter
public class Question extends BasePersistedEntity {

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

    @Column(name = "source_question_id")
    private UUID sourceQuestionId;

    protected Question() {
    }

    public static Question create(UUID questionSetId, QuestionType questionType, String content,
            DifficultyLevel difficultyLevel, SkillType skillType, String questionCategory, int orderNo,
            BigDecimal maxRawScore, String explanation, UUID sourceQuestionId) {
        Question question = new Question();
        question.id = UUID.randomUUID();
        question.questionSetId = questionSetId;
        question.questionType = questionType;
        question.content = content;
        question.difficultyLevel = difficultyLevel;
        question.skillType = skillType;
        question.questionCategory = questionCategory;
        question.orderNo = orderNo;
        question.maxRawScore = maxRawScore;
        question.explanation = explanation;
        question.sourceQuestionId = sourceQuestionId;
        return question;
    }
}
