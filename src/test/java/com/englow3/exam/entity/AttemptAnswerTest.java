package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A graded paper saves one row per answer and one per selected option. If Spring Data cannot tell they are new it reads
 * each row first (a merge) before inserting it: two hundred answers cost hundreds of extra round trips, which on a
 * remote database was the half minute a full test took to submit.
 */
class AttemptAnswerTest {

    @Test
    void anAnswerJustGradedIsNewSoItIsInsertedWithoutBeingReadFirst() {
        AttemptAnswer answer = AttemptAnswer.graded(UUID.randomUUID(), UUID.randomUUID(), true, BigDecimal.ONE,
                Instant.now());

        assertThat(answer.isNew()).isTrue();
        assertThat(answer.getId()).isNotNull();
    }

    @Test
    void aSelectedOptionJustCreatedIsNew() {
        AttemptAnswerOption option = AttemptAnswerOption.selected(UUID.randomUUID(), UUID.randomUUID());

        assertThat(option.isNew()).isTrue();
        assertThat(option.getId()).isNotNull();
    }
}
