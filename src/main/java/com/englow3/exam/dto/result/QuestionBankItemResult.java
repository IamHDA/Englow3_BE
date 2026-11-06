package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionAcceptedAnswer;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSetOption;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SkillType;

/**
 * One row of a question-bank search with everything that answers it: the admin needs the correct answer and its
 * rationale to copy this question into a new paper's content tree. A choice question answers with its options, a
 * TEXT_ENTRY question with its accepted spellings, and a MATCHING question with labels from its set's shared list - so
 * that list travels with it, or a copy would point at labels the new set does not have. Reuses
 * {@link QuestionOptionResult} rather than a second option shape - the two carry the same fields for the same reason.
 *
 * @param setOptions
 *            the shared list of the question's set; empty unless the question is MATCHING
 */
public record QuestionBankItemResult(UUID id, UUID questionSetId, QuestionType questionType, String content,
        DifficultyLevel difficultyLevel, SkillType skillType, String questionCategory, BigDecimal maxRawScore,
        String explanation, List<QuestionOptionResult> options, List<AcceptedAnswer> acceptedAnswers,
        List<String> matchingLabels, List<SetOption> setOptions) {

    public record AcceptedAnswer(String text, boolean caseSensitive, int orderNo) {
    }

    public record SetOption(String label, String content, int orderNo) {
    }

    /**
     * @param matchingLabels
     *            labels of the set entries that answer it, already resolved from their ids
     */
    public static QuestionBankItemResult of(Question question, List<QuestionOption> options,
            List<QuestionAcceptedAnswer> acceptedAnswers, List<String> matchingLabels,
            List<QuestionSetOption> setOptions) {
        boolean matching = question.getQuestionType() == QuestionType.MATCHING;
        return new QuestionBankItemResult(question.getId(), question.getQuestionSetId(), question.getQuestionType(),
                question.getContent(), question.getDifficultyLevel(), question.getSkillType(),
                question.getQuestionCategory(), question.getMaxRawScore(), question.getExplanation(),
                options.stream().map(QuestionOptionResult::of).toList(),
                acceptedAnswers.stream()
                        .map(a -> new AcceptedAnswer(a.getAnswerText(), a.isCaseSensitive(), a.getOrderNo())).toList(),
                matching ? matchingLabels : List.of(), matching
                        ? setOptions.stream().map(o -> new SetOption(o.getLabel(), o.getContent(), o.getOrderNo()))
                                .toList()
                        : List.of());
    }
}
