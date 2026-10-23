package com.englow3.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.englow3.shared.error.TooManyRequestsException;

import lombok.RequiredArgsConstructor;

/**
 * A ceiling on how often one person may do one expensive thing - exporting a whole history, for instance, which reads a
 * dozen tables.
 * <p>
 * In memory and per instance, deliberately. This is a guard against a script or a stuck retry loop, not an accounting
 * system: with two instances a determined caller gets twice the allowance, which is still bounded, and nothing is
 * written to the database to throttle a request that was meant to be cheap to refuse. Anything that must hold exactly -
 * the AI budget - is counted in the database instead.
 */
@Component
@RequiredArgsConstructor
public class UserRateLimiter {

    /** Far above the people one instance serves; only there so a flood of distinct ids cannot grow the map forever. */
    private static final int MAX_TRACKED = 50_000;

    private final Clock clock;

    private record Window(Instant start, int used) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Counts one use of {@code action} by {@code userId}, or refuses it once {@code max} uses have happened inside the
     * current {@code window}. The window opens at the first use and closes after its length, so the allowance renews in
     * steps rather than sliding.
     */
    public void check(UUID userId, String action, int max, Duration window) {
        Instant now = clock.instant();
        if (windows.size() >= MAX_TRACKED) {
            windows.entrySet().removeIf(entry -> !entry.getValue().start().plus(window).isAfter(now));
            if (windows.size() >= MAX_TRACKED) {
                windows.clear();
            }
        }
        Window result = windows.compute(userId + ":" + action, (key, current) -> {
            if (current == null || !current.start().plus(window).isAfter(now)) {
                return new Window(now, 1);
            }
            return new Window(current.start(), current.used() + 1);
        });
        if (result.used() > max) {
            throw new TooManyRequestsException("RATE_LIMITED", "Too many requests. Please try again in a few minutes");
        }
    }
}
