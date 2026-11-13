package com.englow3.ai.service;

import java.time.Duration;

/**
 * How long to wait before trying a failed job again. Pure, so the growth can be checked without a clock or a database.
 * <p>
 * Exponential with a ceiling. Exponential because a provider that just rejected a request is usually still rejecting
 * them a second later, and hammering it turns one outage into a rate-limit ban. The ceiling because unbounded doubling
 * means the eleventh attempt lands next week, by which time the learner waiting on it has gone.
 */
public final class RetryBackoff {

    static final Duration FIRST_DELAY = Duration.ofSeconds(5);
    static final Duration MAX_DELAY = Duration.ofMinutes(10);

    private RetryBackoff() {
    }

    /**
     * @param retryCount
     *            how many attempts have already failed. Zero means the first failure, which waits {@link #FIRST_DELAY}.
     */
    public static Duration delayAfter(int retryCount) {
        if (retryCount <= 0) {
            return FIRST_DELAY;
        }
        // Shifting rather than Math.pow: the exponent is small, and a long cannot overflow before the ceiling clamps.
        // Capped at 30 so the shift itself stays defined however high a retry count a bad row carries.
        long multiplier = 1L << Math.min(retryCount, 30);
        Duration delay = FIRST_DELAY.multipliedBy(multiplier);

        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }
}
