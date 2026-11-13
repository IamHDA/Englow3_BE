package com.englow3.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetryBackoffTest {

    @Test
    void waitsTheFirstDelayAfterTheFirstFailure() {
        assertThat(RetryBackoff.delayAfter(0)).isEqualTo(RetryBackoff.FIRST_DELAY);
    }

    @Test
    void doublesWithEachFailure() {
        assertThat(RetryBackoff.delayAfter(1)).isEqualTo(RetryBackoff.FIRST_DELAY.multipliedBy(2));
        assertThat(RetryBackoff.delayAfter(2)).isEqualTo(RetryBackoff.FIRST_DELAY.multipliedBy(4));
    }

    /** Unbounded doubling puts the eleventh attempt next week, by which time nobody is waiting for it. */
    @Test
    void stopsGrowingAtTheCeiling() {
        assertThat(RetryBackoff.delayAfter(20)).isEqualTo(RetryBackoff.MAX_DELAY);
    }

    /** Nothing writes a negative count, but a shift by a negative number is worse than clamping. */
    @Test
    void treatsANonsenseCountAsTheFirstFailure() {
        assertThat(RetryBackoff.delayAfter(-3)).isEqualTo(RetryBackoff.FIRST_DELAY);
    }

    /** A row carrying an absurd retry count must not overflow the shift into a tiny delay. */
    @Test
    void staysAtTheCeilingForAnAbsurdCount() {
        assertThat(RetryBackoff.delayAfter(Integer.MAX_VALUE)).isEqualTo(RetryBackoff.MAX_DELAY);
    }
}
