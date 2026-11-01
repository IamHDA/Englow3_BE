package com.englow3.roadmap.entity;

import java.util.Optional;

/**
 * A CEFR band as the roadmap reasons about it: ordered, so a plan can say a stage climbs from one band to the next and
 * a level check can average three bands. The user module keeps its own copy of the same values; the two meet only as
 * strings through {@code user.api}.
 */
public enum CefrLevel {
    A1, A2, B1, B2, C1, C2;

    /** 1 for A1 up to 6 for C2, the scale the level check averages on. */
    public int rank() {
        return ordinal() + 1;
    }

    /** The band for a rank, clamped to A1..C2. */
    public static CefrLevel ofRank(int rank) {
        CefrLevel[] all = values();
        return all[Math.max(1, Math.min(all.length, rank)) - 1];
    }

    public static Optional<CefrLevel> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public boolean isAbove(CefrLevel other) {
        return compareTo(other) > 0;
    }
}
