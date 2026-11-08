package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FullTestSittingTest {

    private static final Instant START = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant DURING = START.plus(Duration.ofDays(2));
    private static final Instant AFTER = START.plus(Duration.ofDays(8));

    private final UUID paper = UUID.randomUUID();

    private static BigDecimal b(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static FullTestSitting.Part part(Instant submittedAt, String band) {
        return new FullTestSitting.Part(UUID.randomUUID(), submittedAt, b(band), band == null ? null : DURING);
    }

    private FullTestSitting.Progress progress(String listening, String reading, FullTestSitting.Part... parts) {
        List<FullTestSitting.Part> five = new ArrayList<>(Arrays.asList(parts));
        while (five.size() < 5) {
            five.add(null);
        }
        return new FullTestSitting.Progress(listening == null && reading == null ? null : paper, DURING, b(listening),
                b(reading), five);
    }

    private static FullTestSitting started() {
        return FullTestSitting.start(UUID.randomUUID(), UUID.randomUUID(), START);
    }

    @Test
    void aSittingHasSevenDays() {
        assertThat(started().getDeadlineAt()).isEqualTo(START.plus(Duration.ofDays(7)));
    }

    @Test
    void gradesSavedInTimeStillCountWhenReconciliationRunsAfterTheCutoff() {
        var sitting = started();
        var change = sitting.settle(progress("7.0", "6.0", part(DURING, "6.0"), part(DURING, "6.0"),
                part(DURING, "6.0"), part(DURING, "6.0"), part(DURING, "6.0")), sitting.gradesDueAt().plusSeconds(60));
        assertThat(change.completed()).isTrue();
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.COMPLETED);
    }

    @Test
    void aGradeAfterTheGradingCutoffCannotCompleteAnUnexpiredRow() {
        var sitting = started();
        FullTestSitting.Part[] parts = { part(DURING, null), part(DURING, "6.0"), part(DURING, "6.0"),
                part(DURING, "6.0"), part(DURING, "6.0") };
        sitting.settle(progress("7.0", "6.0", parts), DURING);
        parts[0] = new FullTestSitting.Part(parts[0].attemptId(), DURING, b("6.0"),
                sitting.gradesDueAt().plusSeconds(1));
        var change = sitting.settle(progress("7.0", "6.0", parts), sitting.gradesDueAt().plusSeconds(1));
        assertThat(change.completed()).isFalse();
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.EXPIRED);
        assertThat(sitting.getOverallBand()).isNull();
        assertThat(sitting.getProvisionalBand()).isEqualByComparingTo("6.0");
    }

    @Test
    void theProvisionalBandIsTheLowerOfListeningAndReading() {
        FullTestSitting sitting = started();

        FullTestSitting.Change change = sitting.settle(progress("7.0", "6.0"), DURING);

        assertThat(change.provisionalSet()).isTrue();
        assertThat(sitting.getProvisionalBand()).isEqualByComparingTo("6.0");
        assertThat(sitting.getOverallBand()).isNull();
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.IN_PROGRESS);
    }

    @Test
    void theProvisionalBandIsAnnouncedOnce() {
        FullTestSitting sitting = started();
        sitting.settle(progress("7.0", "6.0"), DURING);

        assertThat(sitting.settle(progress("7.0", "6.0"), DURING).provisionalSet()).isFalse();
    }

    @Test
    void allFourSkillsGradedGiveTheOverallBandTheIeltsWay() {
        FullTestSitting sitting = started();

        FullTestSitting.Change change = sitting.settle(progress("7.0", "6.5", part(DURING, "6.0"), part(DURING, "6.0"),
                part(DURING, "6.0"), part(DURING, "5.5"), part(DURING, "6.0")), DURING);

        assertThat(change.completed()).isTrue();
        assertThat(sitting.getWritingBand()).isEqualByComparingTo("6.0");
        assertThat(sitting.getSpeakingBand()).isEqualByComparingTo("6.0");
        assertThat(sitting.getOverallBand()).isEqualByComparingTo("6.5");
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.COMPLETED);
        assertThat(sitting.getCompletedAt()).isEqualTo(DURING);
    }

    @Test
    void anAnswerSubmittedAfterTheDeadlineDoesNotCount() {
        FullTestSitting sitting = started();

        sitting.settle(progress("7.0", "6.5", part(AFTER, "6.0")), DURING);

        assertThat(sitting.getWriting1AttemptId()).isNull();
    }

    @Test
    void anAnswerSubmittedBeforeTheSittingStartedDoesNotCount() {
        FullTestSitting sitting = started();

        sitting.settle(progress("7.0", "6.5", part(START.minusSeconds(60), "6.0")), DURING);

        assertThat(sitting.getWriting1AttemptId()).isNull();
    }

    @Test
    void anAttachedAnswerIsNeverSwappedForALaterOne() {
        FullTestSitting sitting = started();
        FullTestSitting.Part first = part(DURING, null);
        sitting.settle(progress("7.0", "6.5", first), DURING);

        sitting.settle(progress("7.0", "6.5", part(DURING, "9.0")), DURING);

        assertThat(sitting.getWriting1AttemptId()).isEqualTo(first.attemptId());
    }

    @Test
    void anAnswerGradedAfterTheDeadlineStillCountsAndCompletesTheSitting() {
        FullTestSitting sitting = started();
        List<FullTestSitting.Part> ungraded = List.of(part(DURING, null), part(DURING, null), part(DURING, null),
                part(DURING, null), part(DURING, null));
        sitting.settle(progress("7.0", "6.5", ungraded.toArray(FullTestSitting.Part[]::new)), DURING);

        sitting.settle(progress("7.0", "6.5", ungraded.toArray(FullTestSitting.Part[]::new)), AFTER);
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.AWAITING_GRADES);

        FullTestSitting.Part[] graded = ungraded.stream()
                .map(p -> new FullTestSitting.Part(p.attemptId(), p.submittedAt(), b("6.0"), AFTER.plusSeconds(3600)))
                .toArray(FullTestSitting.Part[]::new);
        assertThat(sitting.settle(progress("7.0", "6.5", graded), AFTER.plusSeconds(3600)).completed()).isTrue();
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.COMPLETED);
    }

    @Test
    void theDeadlinePassingWithAPartMissingExpiresTheSittingAndKeepsTheProvisionalBand() {
        FullTestSitting sitting = started();
        sitting.settle(progress("7.0", "6.0", part(DURING, "6.0")), DURING);

        sitting.settle(progress("7.0", "6.0", part(DURING, "6.0")), AFTER);

        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.EXPIRED);
        assertThat(sitting.getProvisionalBand()).isEqualByComparingTo("6.0");
        assertThat(sitting.getOverallBand()).isNull();
    }

    @Test
    void aSettledSittingNoLongerChanges() {
        FullTestSitting sitting = started();
        sitting.settle(progress(null, null), AFTER);

        FullTestSitting.Change change = sitting.settle(progress("9.0", "9.0"), AFTER);

        assertThat(change.provisionalSet()).isFalse();
        assertThat(sitting.getListeningBand()).isNull();
    }

    @Test
    void aSittingStillShortOfAGradeAWeekAfterItsDeadlineExpiresWithItsProvisionalBand() {
        FullTestSitting sitting = started();
        FullTestSitting.Part[] ungraded = { part(DURING, "6.0"), part(DURING, null), part(DURING, "6.0"),
                part(DURING, "6.0"), part(DURING, "6.0") };
        sitting.settle(progress("7.0", "6.5", ungraded), DURING);

        Instant due = sitting.gradesDueAt();
        assertThat(due).isEqualTo(sitting.getDeadlineAt().plus(FullTestSitting.GRADING_WINDOW));
        sitting.settle(progress("7.0", "6.5", ungraded), due);
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.AWAITING_GRADES);

        sitting.settle(progress("7.0", "6.5", ungraded), due.plusSeconds(1));
        assertThat(sitting.getStatus()).isEqualTo(FullTestSittingStatus.EXPIRED);
        assertThat(sitting.getProvisionalBand()).isEqualByComparingTo("6.5");
        assertThat(sitting.getOverallBand()).isNull();
    }

    @Test
    void aGradeArrivingInTimeStillCompletesTheSittingOnTheLastDay() {
        FullTestSitting sitting = started();
        FullTestSitting.Part late = part(DURING, null);
        FullTestSitting.Part[] waiting = { part(DURING, "6.0"), late, part(DURING, "6.0"), part(DURING, "6.0"),
                part(DURING, "6.0") };
        sitting.settle(progress("7.0", "6.5", waiting), AFTER);

        waiting[1] = new FullTestSitting.Part(late.attemptId(), late.submittedAt(), b("6.0"), sitting.gradesDueAt());
        assertThat(sitting.settle(progress("7.0", "6.5", waiting), sitting.gradesDueAt()).completed()).isTrue();
    }
}
