package com.englow3.exam.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QuestionNumberingTest {

    private final UUID listening = UUID.randomUUID();
    private final UUID reading = UUID.randomUUID();
    private final UUID q1 = UUID.randomUUID();
    private final UUID q2 = UUID.randomUUID();
    private final UUID q3 = UUID.randomUUID();
    private final UUID q4 = UUID.randomUUID();

    private List<QuestionNumbering.Slot> paper() {
        return List.of(new QuestionNumbering.Slot(q1, listening, 1), new QuestionNumbering.Slot(q2, listening, 1),
                new QuestionNumbering.Slot(q3, reading, 1), new QuestionNumbering.Slot(q4, reading, 1));
    }

    @Test
    void ieltsStartsEverySectionAgainAtOne() {
        var numbers = QuestionNumbering.assign(paper(), true);

        assertThat(numbers.get(q2)).isEqualTo(new QuestionNumbering.Numbers(2, 2));
        assertThat(numbers.get(q3)).isEqualTo(new QuestionNumbering.Numbers(1, 1));
    }

    @Test
    void toeicCountsOnThroughThePaper() {
        var numbers = QuestionNumbering.assign(paper(), false);

        assertThat(numbers.get(q3)).isEqualTo(new QuestionNumbering.Numbers(3, 3));
        assertThat(numbers.get(q4)).isEqualTo(new QuestionNumbering.Numbers(4, 4));
    }

    @Test
    void aChooseTwoQuestionTakesTwoNumbersAndPushesTheRestOn() {
        var numbers = QuestionNumbering.assign(List.of(new QuestionNumbering.Slot(q1, reading, 1),
                new QuestionNumbering.Slot(q2, reading, 2), new QuestionNumbering.Slot(q3, reading, 1)), true);

        assertThat(numbers.get(q2)).isEqualTo(new QuestionNumbering.Numbers(2, 3));
        assertThat(numbers.get(q3)).isEqualTo(new QuestionNumbering.Numbers(4, 4));
    }
}
