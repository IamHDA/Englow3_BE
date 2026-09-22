package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SkillType;

/**
 * One row of a question-bank search, options included: the admin needs the correct answer and its rationale to copy
 * this question into a new paper's content tree. Reuses {@link ExamDetailResult.QuestionOptionResult} rather than a
 * second option shape - the two carry the same fields for the same reason.
 */
public record QuestionBankItemResult(UUID id, UUID questionSetId, QuestionType questionType, String content,
        DifficultyLevel difficultyLevel, SkillType skillType, String questionCategory, BigDecimal maxRawScore,
        String explanation, List<ExamDetailResult.QuestionOptionResult> options) {

    public static QuestionBankItemResult of(Question question, List<QuestionOption> options) {
        return new QuestionBankItemResult(question.getId(), question.getQuestionSetId(), question.getQuestionType(),
                question.getContent(), question.getDifficultyLevel(), question.getSkillType(),
                question.getQuestionCategory(), question.getMaxRawScore(), question.getExplanation(),
                options.stream().map(ExamDetailResult.QuestionOptionResult::of).toList());
    }
}
