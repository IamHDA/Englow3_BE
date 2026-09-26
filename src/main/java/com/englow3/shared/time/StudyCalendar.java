package com.englow3.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where one day ends and the next begins, for every daily figure: streaks, today's tasks and quests, the per-day charts
 * and the daily AI allowance.
 * <p>
 * These used to be UTC days. The learners are in Vietnam, seven hours ahead, so their day turned over at seven in the
 * morning: practice at half past six counted towards yesterday, a streak could break over a night's sleep, and today's
 * quests reset mid-morning. One zone for everyone - there is no per-learner zone to use - set by
 * {@code app.calendar.zone}.
 */
@Component
public class StudyCalendar {

    private final Clock clock;
    private final ZoneId zone;

    /** Reads the application's {@link Clock}, so a test that fixes the time fixes the day as well. */
    public StudyCalendar(Clock clock, @Value("${app.calendar.zone:Asia/Ho_Chi_Minh}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    /** The zone's id, for SQL that buckets timestamps into days with {@code at time zone :zone}. */
    public String zoneId() {
        return zone.getId();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public Instant startOfToday() {
        return today().atStartOfDay(zone).toInstant();
    }

    /** The day an instant falls on here. */
    public LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }
}
