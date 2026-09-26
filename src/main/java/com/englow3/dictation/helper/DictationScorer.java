package com.englow3.dictation.helper;

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

        int correct = 0;
        for (int i = 0; i < expectedWords.size() && i < actualWords.size(); i++) {
            if (expectedWords.get(i).equals(actualWords.get(i))) {
                correct++;
            }
        }

        BigDecimal accuracy = expectedWords.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(expectedWords.size()), 2, RoundingMode.HALF_UP);

        return new Score(accuracy, correct, expectedWords.size());
    }
}
