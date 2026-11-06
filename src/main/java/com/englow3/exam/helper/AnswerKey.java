package com.englow3.exam.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.englow3.exam.entity.QuestionType;

/**
 * How one answer to one question is graded. Pure: it is handed the question's key and the learner's answer and decides
 * the points, so every shape is tested without a database (docs/decisions.md, D-IELTS-3 and D-IELTS-4).
 *
 * @param type
 *            the question's shape
 * @param maxRawScore
 *            what the question is worth
 * @param correctOptionIds
 *            the options marked correct; empty on a TEXT_ENTRY question
 * @param acceptedAnswers
 *            the spellings a TEXT_ENTRY question accepts; empty on a choice question
 */
public record AnswerKey(QuestionType type, BigDecimal maxRawScore, Set<UUID> correctOptionIds,
        List<Accepted> acceptedAnswers) {

    public AnswerKey {
        correctOptionIds = Set.copyOf(correctOptionIds);
        acceptedAnswers = List.copyOf(acceptedAnswers);
    }

    /** One accepted spelling. Case is ignored unless the author said otherwise. */
    public record Accepted(String text, boolean caseSensitive) {
    }

    /** The points an answer earned, and whether it earned all of them. */
    public record Grade(boolean correct, BigDecimal awarded) {
        static Grade none() {
            return new Grade(false, BigDecimal.ZERO);
        }
    }

    /** How many options a learner may choose: N on a "choose N letters" question, one on a single choice. */
    public int choiceLimit() {
        return type == QuestionType.MULTIPLE_CHOICE ? correctOptionIds.size() : 1;
    }

    /** How many question numbers this question takes on the paper - a "choose TWO" question is numbered 21-22. */
    public int numberSpan() {
        return type == QuestionType.MULTIPLE_CHOICE ? Math.max(1, correctOptionIds.size()) : 1;
    }

    public Grade grade(Set<UUID> selectedOptionIds, String textAnswer) {
        return switch (type) {
            case SINGLE_CHOICE -> !correctOptionIds.isEmpty() && selectedOptionIds.equals(correctOptionIds)
                    ? new Grade(true, maxRawScore)
                    : Grade.none();
            case MULTIPLE_CHOICE -> gradeChoices(selectedOptionIds);
            case TEXT_ENTRY -> accepts(textAnswer) ? new Grade(true, maxRawScore) : Grade.none();
        };
    }

    /**
     * Each correct option chosen earns an equal share. Choosing more than N earns nothing: otherwise ticking every box
     * would collect the whole question.
     */
    private Grade gradeChoices(Set<UUID> selected) {
        if (correctOptionIds.isEmpty() || selected.size() > correctOptionIds.size()) {
            return Grade.none();
        }
        long hits = selected.stream().filter(correctOptionIds::contains).count();
        if (hits == 0) {
            return Grade.none();
        }
        BigDecimal awarded = maxRawScore.multiply(BigDecimal.valueOf(hits))
                .divide(BigDecimal.valueOf(correctOptionIds.size()), 2, RoundingMode.HALF_UP);
        return new Grade(hits == correctOptionIds.size(), awarded);
    }

    private boolean accepts(String textAnswer) {
        String given = normalize(textAnswer);
        if (given.isEmpty()) {
            return false;
        }
        return acceptedAnswers.stream().anyMatch(accepted -> {
            String expected = normalize(accepted.text());
            return accepted.caseSensitive() ? expected.equals(given)
                    : expected.toLowerCase(Locale.ROOT).equals(given.toLowerCase(Locale.ROOT));
        });
    }

    /** Leading, trailing and repeated whitespace never makes an answer wrong. Nothing else is forgiven. */
    static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }
}
