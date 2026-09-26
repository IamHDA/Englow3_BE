package com.englow3.learning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Marks a transcription. Pure, like the flashcard scheduler and the quiz grader, so the rules can be tested without a
 * database. It owns the one definition of what a word is. The interface highlights the differences itself, but the
 * number it displays is this one - a second implementation of "how many words did they get right" is how two answers to
 * the same question start disagreeing.
 */
public final class DictationScorer {

    /**
     * Punctuation is stripped before comparing. A learner transcribing speech hears no commas, so marking them down for
     * a missing one would be marking punctuation rather than listening.
     */
    private static final String PUNCTUATION = "[\\p{Punct}‘’“”]";

    /**
     * The accuracy at which a sentence counts as cleared.
     * <p>
     * Here rather than in each service that asks the question. Three of them do - the lesson's own progress, the
     * statistics screen, and the daily path's "unfinished" list - and while each kept its own copy they agreed only by
     * coincidence. A sentence that is finished on one screen and outstanding on another is not a rounding difference;
     * it is the system giving two answers about the same work.
     * <p>
     * Eighty leaves room for the odd misheard word without letting a transcription that missed the point through.
     */
    public static final BigDecimal COMPLETION_THRESHOLD = BigDecimal.valueOf(80);

    private DictationScorer() {
    }

    /** Null is not cleared: no measurement is not the same as a low one. */
    public static boolean cleared(BigDecimal accuracyPercent) {
        return accuracyPercent != null && accuracyPercent.compareTo(COMPLETION_THRESHOLD) >= 0;
    }

    public record Score(BigDecimal accuracyPercent, int correctWordCount, int totalWordCount) {
    }

    /** Case-insensitive, punctuation-free, whitespace-collapsed. Empty input scores zero rather than failing. */
    public static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            String cleaned = token.replaceAll(PUNCTUATION, "").toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) {
                words.add(cleaned);
            }
        }
        return words;
    }

    /**
     * Counted position by position rather than as a set. A set would call "the cat sat on the mat" and "mat the on sat
     * cat the" a perfect match, which is the opposite of what transcription practice measures. Extra words the learner
     * invented are not counted as correct, and words they missed simply never match - both show up as a lower score
     * without needing a separate penalty.
     */
    public static Score score(String expected, String actual) {
        List<String> expectedWords = words(expected);
        List<String> actualWords = words(actual);

        // Words matched in order, allowing for words missed or added along the way - the longest common subsequence.
        // Compared position by position, one word left out at the start shifted every word after it and a nearly
        // perfect answer scored zero, while the practice screen, which aligns the same way as this, showed a single
        // missing word.
        int correct = longestCommonSubsequence(expectedWords, actualWords);

        // Out of the longer of the two, so typing the sentence and then a string of guesses is not a hundred percent.
        int outOf = Math.max(expectedWords.size(), actualWords.size());
        BigDecimal accuracy = expectedWords.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(outOf), 2,
                        RoundingMode.HALF_UP);
        return new Score(accuracy, correct, expectedWords.size());
    }

    private static int longestCommonSubsequence(List<String> left, List<String> right) {
        int[] previous = new int[right.size() + 1];
        for (String word : left) {
            int[] current = new int[right.size() + 1];
            for (int j = 1; j <= right.size(); j++) {
                current[j] = word.equals(right.get(j - 1)) ? previous[j - 1] + 1
                        : Math.max(previous[j], current[j - 1]);
            }
            previous = current;
        }
        return previous[right.size()];
    }
}
