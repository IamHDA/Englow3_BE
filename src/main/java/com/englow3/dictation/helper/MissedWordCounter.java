package com.englow3.dictation.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which words a learner keeps getting wrong, counted across their recent transcriptions. Nothing stores a per-word
 * result - an attempt keeps the answer and the score, not a breakdown - so this recomputes it from the pairs. That is
 * deliberate: storing a row per word would multiply the attempt table by the length of every sentence, to answer one
 * screen. It reuses {@link DictationScorer#words} rather than splitting text itself, so "what is a word" has one
 * definition.
 */
public final class MissedWordCounter {

    private MissedWordCounter() {
    }

    public record MissedWord(String word, long missedCount, long correctCount, int accuracyPercent) {
    }

    /**
     * @param attempts
     *            pairs of (expected sentence, what the learner typed)
     * @param limit
     *            how many of the worst words to return
     */
    public static List<MissedWord> count(List<String[]> attempts, int limit) {
        Map<String, long[]> tally = new LinkedHashMap<>();

        for (String[] attempt : attempts) {
            List<String> expected = DictationScorer.words(attempt[0]);
            List<String> actual = DictationScorer.words(attempt[1]);

            for (int i = 0; i < expected.size(); i++) {
                String word = expected.get(i);
                // A word beyond the end of what they typed is missed, not mistyped - both count against it, and the
                // distinction is not one the screen shows.
                boolean correct = i < actual.size() && actual.get(i).equals(word);
                long[] counts = tally.computeIfAbsent(word, ignored -> new long[2]);
                if (correct) {
                    counts[0]++;
                } else {
                    counts[1]++;
                }
            }
        }

        List<MissedWord> missed = new ArrayList<>();
        tally.forEach((word, counts) -> {
            if (counts[1] == 0) {
                return;
            }
            long total = counts[0] + counts[1];
            missed.add(new MissedWord(word, counts[1], counts[0], (int) Math.round(100.0 * counts[0] / total)));
        });

        // Worst first, and by accuracy rather than by raw misses: a word missed twice out of two is a bigger problem
        // than one missed three times out of thirty.
        missed.sort(Comparator.comparingInt(MissedWord::accuracyPercent)
                .thenComparing(Comparator.comparingLong(MissedWord::missedCount).reversed()));

        return missed.size() > limit ? missed.subList(0, limit) : missed;
    }
}
