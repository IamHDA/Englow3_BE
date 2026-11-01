package com.englow3.roadmap.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LevelCheckWeightTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private static LevelCheckWeight weights(int vocabulary, int listening, int speaking) {
        return LevelCheckWeight.from(UUID.randomUUID(), "WORK", vocabulary, listening, speaking, 5, NOW, null);
    }

    @Test
    void acceptsWeightsThatAddUpToTheWhole() {
        assertThat(weights(40, 30, 30).getSpeaking()).isEqualTo(30);
        assertThat(weights(100, 0, 0).getVocabulary()).isEqualTo(100);
    }

    @Test
    void refusesWeightsThatDoNotAddUpToOneHundred() {
        assertThatThrownBy(() -> weights(40, 30, 20)).hasFieldOrPropertyWithValue("code", "LEVEL_CHECK_WEIGHTS_INVALID")
                .hasMessageContaining("add up to 100");
    }

    /**
     * A check without its spoken part scales the written weights up to the whole; from zero there is nothing to scale.
     */
    @Test
    void refusesWeightsWithNothingOnTheWrittenSide() {
        assertThatThrownBy(() -> weights(0, 0, 100)).hasMessageContaining("cannot both be 0");
    }

    @Test
    void refusesANegativeWeightOrAMalformedPurpose() {
        assertThatThrownBy(() -> weights(110, -10, 0)).hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> LevelCheckWeight.from(UUID.randomUUID(), "work", 40, 30, 30, 0, NOW, null))
                .hasMessageContaining("upper-case");
    }
}
