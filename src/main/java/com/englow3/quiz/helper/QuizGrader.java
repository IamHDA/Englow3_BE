package com.englow3.quiz.helper;

import java.util.List;
import java.util.Locale;

import com.englow3.quiz.entity.QuizQuestionType;

/**
 * Marks one answer. A pure function of (question shape, learner response): no repository, no entities beyond the plain
 * values it is handed, so every one of the five question types can be tested exhaustively without a database. All five
 * compare case-insensitively with whitespace collapsed. A learner who types the right words is right; making them also
 * match our spacing and capitalisation would be marking typing, not English.
 */
public final class QuizGrader {

    /** What the grader needs to know about a question, flattened out of its three child tables. */
    public record GradableQuestion(QuizQuestionType type, List<String> correctOptionIds, List<String> acceptedAnswers,
            List<String> correctWords, List<String> correctRightTexts) {
    }

    /**
     * The separator between the halves of a MATCHING answer. Chosen because it cannot occur in a clause the way a comma
     * can - splitting on a comma would break the first question whose left half contains one.
     */
    public static final String MATCHING_SEPARATOR = "|";

    /** REWRITE and REORDER answers arrive as the chosen words in order, joined by spaces. */
    private static final String WORD_SEPARATOR = " ";

    private QuizGrader() {
    }

    public static boolean isCorrect(GradableQuestion question, String response) {
        String answer = response == null ? "" : response.trim();
        if (answer.isEmpty()) {
            return false;
        }

        return switch (question.type()) {
            case MULTIPLE_CHOICE -> question.correctOptionIds().contains(answer);
            case FILL_BLANK -> question.acceptedAnswers().stream().anyMatch(accepted -> equalText(accepted, answer));
            // The sentence as a whole, not word by word against the tiles: a tile may be more than one word ("in
            // spite of"), and splitting the answer on spaces made such a question impossible to get right.
            case REWRITE, REORDER -> equalText(String.join(WORD_SEPARATOR, question.correctWords()), answer);
            case MATCHING -> equalWordSequence(question.correctRightTexts(), splitMatching(answer));
        };
    }

    /** What the review screen shows beside the learner's answer. */
    public static String correctAnswerText(GradableQuestion question, List<String> correctOptionLabels) {
        return switch (question.type()) {
            case MULTIPLE_CHOICE -> String.join(", ", correctOptionLabels);
            case FILL_BLANK -> String.join(" / ", question.acceptedAnswers());
            case REWRITE, REORDER -> String.join(WORD_SEPARATOR, question.correctWords());
            case MATCHING -> String.join(MATCHING_SEPARATOR, question.correctRightTexts());
        };
    }

    /**
     * Split on the literal separator, not a regex: {@code "|"} is alternation in a pattern and would match the empty
     * string between every character. {@code -1} keeps trailing blanks, so an unanswered last pair stays a blank entry
     * and shortens nothing.
     */
    private static List<String> splitMatching(String answer) {
        return List.of(answer.split(java.util.regex.Pattern.quote(MATCHING_SEPARATOR), -1));
    }

    private static boolean equalWordSequence(List<String> expected, List<String> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!equalText(expected.get(i), actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalText(String expected, String actual) {
        return normalise(expected).equals(normalise(actual));
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
