package com.englow3.roadmap.helper;

import java.util.List;

import com.englow3.roadmap.entity.CefrLevel;

/**
 * Combines the three parts of a level check into one band: a weighted mean on A1 = 1 ... C2 = 6, rounded down so a
 * learner is never placed above what they showed. Without a spoken part the two written weights are scaled up to the
 * whole.
 */
public final class LevelCombiner {

    private LevelCombiner() {
    }

    /** Percentages for vocabulary/grammar, listening and speaking. */
    public record Weights(int vocabulary, int listening, int speaking) {

        public String toJson(boolean withSpeaking) {
            return withSpeaking
                    ? "{\"vocabulary\":%d,\"listening\":%d,\"speaking\":%d}".formatted(vocabulary, listening, speaking)
                    : "{\"vocabulary\":%d,\"listening\":%d,\"speaking\":0}".formatted(vocabulary, listening);
        }
    }

    public record Result(CefrLevel level, String appliedWeights) {
    }

    static final Weights SPEAKING_FIRST = new Weights(35, 30, 35);
    static final Weights DEFAULT = new Weights(40, 30, 30);

    /**
     * Weights by learning purpose. Speaking counts for more for those learning to talk to people (conversation,
     * travel); otherwise vocabulary and grammar lead.
     */
    public static Weights forPurposes(List<String> purposeCodes) {
        if (purposeCodes != null && (purposeCodes.contains("COMMUNICATION") || purposeCodes.contains("TRAVEL"))) {
            return SPEAKING_FIRST;
        }
        return DEFAULT;
    }

    /**
     * @param speaking
     *            null when the spoken part was skipped
     */
    public static Result combine(CefrLevel vocabulary, CefrLevel listening, CefrLevel speaking, Weights weights) {
        boolean withSpeaking = speaking != null;
        long total = weights.vocabulary() + weights.listening() + (withSpeaking ? weights.speaking() : 0);
        long weighted = (long) vocabulary.rank() * weights.vocabulary() + (long) listening.rank() * weights.listening()
                + (withSpeaking ? (long) speaking.rank() * weights.speaking() : 0);
        // Integer division is the rounding down: 3.99 is still B1.
        return new Result(CefrLevel.ofRank((int) (weighted / total)), weights.toJson(withSpeaking));
    }
}
