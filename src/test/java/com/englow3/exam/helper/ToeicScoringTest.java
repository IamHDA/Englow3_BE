package com.englow3.exam.helper;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.englow3.exam.dto.command.ConfigureToeicScoresCommand.Row;
import com.englow3.exam.entity.*;
import com.englow3.shared.error.BadRequestException;

class ToeicScoringTest {
    private List<Row> table() {
        return new ArrayList<>(List.of(new Row(SectionType.LISTENING, 0, 99, new BigDecimal("5")),
                new Row(SectionType.LISTENING, 100, 100, new BigDecimal("495")),
                new Row(SectionType.READING, 0, 99, new BigDecimal("5")),
                new Row(SectionType.READING, 100, 100, new BigDecimal("495"))));
    }

    @Test
    void acceptsACompleteMonotonicTable() {
        assertThatCode(() -> ToeicScoring.requireCompleteTable(table())).doesNotThrowAnyException();
    }

    @Test
    void refusesGapsOverlapsAndInvalidScores() {
        for (Row bad : List.of(new Row(SectionType.LISTENING, 100, 100, new BigDecimal("490")),
                new Row(SectionType.READING, 100, 100, new BigDecimal("494")),
                new Row(SectionType.WRITING, 0, 100, BigDecimal.TEN))) {
            var rows = table();
            rows.add(bad);
            assertThatThrownBy(() -> ToeicScoring.requireCompleteTable(rows)).isInstanceOf(BadRequestException.class);
        }
        var gap = table();
        gap.removeFirst();
        assertThatThrownBy(() -> ToeicScoring.requireCompleteTable(gap)).isInstanceOf(BadRequestException.class);
        var decreasing = table();
        decreasing.set(0, new Row(SectionType.LISTENING, 0, 99, new BigDecimal("495")));
        decreasing.set(1, new Row(SectionType.LISTENING, 100, 100, new BigDecimal("5")));
        assertThatThrownBy(() -> ToeicScoring.requireCompleteTable(decreasing)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void anIncompleteOrUnconvertedSittingHasNoTotal() {
        UUID id = UUID.randomUUID();
        var listening = AttemptSectionResult.of(id, SectionType.LISTENING, new BigDecimal("40"), new BigDecimal("100"),
                new BigDecimal("250"), 40, 100);
        var reading = AttemptSectionResult.of(id, SectionType.READING, new BigDecimal("40"), new BigDecimal("100"),
                new BigDecimal("300"), 40, 100);
        assertThat(ToeicScoring.total(List.of(listening, reading))).contains(new BigDecimal("550"));
        assertThat(ToeicScoring.total(List.of(listening))).isEmpty();
        var partial = AttemptSectionResult.of(id, SectionType.READING, BigDecimal.ONE, BigDecimal.TEN,
                new BigDecimal("300"), 1, 10);
        assertThat(ToeicScoring.total(List.of(listening, partial))).isEmpty();
    }
}
