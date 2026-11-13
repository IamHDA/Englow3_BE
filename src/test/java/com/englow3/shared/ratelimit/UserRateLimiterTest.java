package com.englow3.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.TooManyRequestsException;

class UserRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** A clock the test moves by hand. */
    private static final class Hand extends Clock {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final Hand clock = new Hand();
    private final UserRateLimiter limiter = new UserRateLimiter(clock);
    private final UUID user = UUID.randomUUID();

    @Test
    void allowsUpToTheLimitThenRefuses() {
        for (int i = 0; i < 3; i++) {
            limiter.check(user, "export", 3, WINDOW);
        }

        assertThatThrownBy(() -> limiter.check(user, "export", 3, WINDOW)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void renewsWhenTheWindowHasPassed() {
        for (int i = 0; i < 3; i++) {
            limiter.check(user, "export", 3, WINDOW);
        }

        clock.now = clock.now.plus(WINDOW).plusSeconds(1);

        assertThatCode(() -> limiter.check(user, "export", 3, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    void keepsOnePersonAndOneActionApartFromAnother() {
        for (int i = 0; i < 3; i++) {
            limiter.check(user, "export", 3, WINDOW);
        }

        assertThatCode(() -> limiter.check(UUID.randomUUID(), "export", 3, WINDOW)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(user, "erase", 3, WINDOW)).doesNotThrowAnyException();
    }
}
