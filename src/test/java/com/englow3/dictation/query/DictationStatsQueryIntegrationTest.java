package com.englow3.dictation.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class DictationStatsQueryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-03-15T10:00:00Z");
    private static final Instant EPOCH = Instant.EPOCH;

    @Autowired
    private DictationStatsQuery dictationStats;

    @Autowired
    private JdbcClient jdbc;

    private LearnerFixture fixture;
    private UUID learner;

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
        learner = fixture.learner();
        lesson = fixture.publishedDictationLesson("Airport", learner);
        sentence = fixture.dictationSentence(lesson, 1, "The cat sat on the mat.");
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    private UUID lesson;
    private UUID sentence;

    @Test
    void averagesTheAccuracyOfEveryAttemptInTheWindow() {
        fixture.dictationAttempt(learner, lesson, sentence, "a", new BigDecimal("100.00"), daysAgo(1));
        fixture.dictationAttempt(learner, lesson, sentence, "b", new BigDecimal("50.00"), daysAgo(2));

        assertThat(dictationStats.averageAccuracy(learner, EPOCH)).isEqualTo(75);
    }

    /**
     * Two different figures on purpose. The line count is distinct sentences - retyping one line is not two lines
     * practised - while the average is over every attempt, because an average of bests would flatter the learner's
     * actual accuracy.
     */
    @Test
    void countsLinesOnceButAveragesOverEveryAttempt() {
        fixture.dictationAttempt(learner, lesson, sentence, "a", new BigDecimal("100.00"), daysAgo(1));
        fixture.dictationAttempt(learner, lesson, sentence, "b", new BigDecimal("0.00"), daysAgo(1));

        assertThat(dictationStats.sentencesPractised(learner, EPOCH)).isEqualTo(1);
        assertThat(dictationStats.averageAccuracy(learner, EPOCH)).isEqualTo(50);
    }

    @Test
    void answersZeroRatherThanFailingForALearnerWhoHasTypedNothing() {
        assertThat(dictationStats.averageAccuracy(learner, EPOCH)).isZero();
        assertThat(dictationStats.sentencesPractised(learner, EPOCH)).isZero();
        assertThat(dictationStats.listeningSeconds(learner, EPOCH)).isZero();
    }

    /** Counted once per lesson cleared, whatever the threshold is set to. */
    @Test
    void countsALessonAsDoneOnlyWhenEverySentenceClears() {
        UUID second = fixture.dictationSentence(lesson, 2, "A second line.");
        fixture.dictationAttempt(learner, lesson, sentence, "a", new BigDecimal("100.00"), daysAgo(1));

        assertThat(dictationStats.lessonsCompleted(learner, new BigDecimal("80"))).isZero();

        fixture.dictationAttempt(learner, lesson, second, "b", new BigDecimal("90.00"), daysAgo(1));

        assertThat(dictationStats.lessonsCompleted(learner, new BigDecimal("80"))).isEqualTo(1);
    }

    /**
     * What the missed-word breakdown is derived from. It re-reads the transcripts rather than storing a per-word table,
     * so the pair has to come back intact - expected first, then what the learner typed.
     */
    @Test
    void handsBackBothSidesOfEachAttempt() {
        fixture.dictationAttempt(learner, lesson, sentence, "The cat sat on the hat.", new BigDecimal("83.33"),
                daysAgo(1));

        assertThat(dictationStats.recentAttemptTexts(learner, EPOCH, 10)).singleElement().satisfies(text -> {
            assertThat(text.expected()).isEqualTo("The cat sat on the mat.");
            assertThat(text.actual()).isEqualTo("The cat sat on the hat.");
        });
    }

    /** The review queue offers lines still below the threshold, worst first. */
    @Test
    void queuesTheWorstLineFirstAndLeavesOutClearedOnes() {
        UUID bad = fixture.dictationSentence(lesson, 2, "A hard line.");
        fixture.dictationAttempt(learner, lesson, sentence, "perfect", new BigDecimal("100.00"), daysAgo(1));
        fixture.dictationAttempt(learner, lesson, bad, "wrong", new BigDecimal("20.00"), daysAgo(1));

        var queue = dictationStats.mistakeQueue(learner, new BigDecimal("80"), 50);

        assertThat(queue).extracting(DictationStatsQuery.MistakeSentence::sentenceId).contains(bad)
                .doesNotContain(sentence);
    }

    /**
     * The review queue carries no transcript. It used to, and the screen graded against it in the browser without
     * telling the server - so a reviewed line was never recorded and came back on the next visit.
     */
    @Test
    void queuesALineWithoutItsAnswer() {
        fixture.dictationAttempt(learner, lesson, sentence, "wrong", new BigDecimal("20.00"), daysAgo(1));

        var line = dictationStats.mistakeQueue(learner, new BigDecimal("80"), 50).stream()
                .filter(queued -> queued.sentenceId().equals(sentence)).findFirst().orElseThrow();

        for (var component : DictationStatsQuery.MistakeSentence.class.getRecordComponents()) {
            Object value;
            try {
                value = component.getAccessor().invoke(line);
            } catch (ReflectiveOperationException unreachable) {
                throw new IllegalStateException(unreachable);
            }
            if (value instanceof String text) {
                assertThat(text).as(component.getName()).doesNotContain("The cat sat on the mat");
            }
        }
    }

    /** A line cut from a longer recording keeps its window, or the review plays the whole passage. */
    @Test
    void carriesTheLinesWindowIntoItsRecording() {
        jdbc.sql("update dictation_sentences set audio_start_ms = 1200, audio_end_ms = 3400 where id = :id")
                .param("id", sentence).update();
        fixture.dictationAttempt(learner, lesson, sentence, "wrong", new BigDecimal("20.00"), daysAgo(1));

        var line = dictationStats.mistakeQueue(learner, new BigDecimal("80"), 50).stream()
                .filter(queued -> queued.sentenceId().equals(sentence)).findFirst().orElseThrow();

        assertThat(line.audioStartMs()).isEqualTo(1200);
        assertThat(line.audioEndMs()).isEqualTo(3400);
    }

    /** A line the learner later got right leaves the queue - the queue is work outstanding, not a history. */
    @Test
    void dropsALineOnceItsBestAttemptClears() {
        fixture.dictationAttempt(learner, lesson, sentence, "wrong", new BigDecimal("20.00"), daysAgo(2));
        fixture.dictationAttempt(learner, lesson, sentence, "right", new BigDecimal("100.00"), daysAgo(1));

        assertThat(dictationStats.mistakeQueue(learner, new BigDecimal("80"), 50))
                .noneMatch(line -> line.sentenceId().equals(sentence));
    }

    @Test
    void groupsAccuracyByDay() {
        fixture.dictationAttempt(learner, lesson, sentence, "a", new BigDecimal("100.00"), daysAgo(1));
        fixture.dictationAttempt(learner, lesson, sentence, "b", new BigDecimal("60.00"), daysAgo(1));
        fixture.dictationAttempt(learner, lesson, sentence, "c", new BigDecimal("80.00"), daysAgo(3));

        var byDay = dictationStats.accuracyByDay(learner, EPOCH);

        assertThat(byDay).hasSize(2);
        assertThat(byDay).anySatisfy(day -> assertThat(day.accuracyPercent()).isEqualTo(80));
    }
}
