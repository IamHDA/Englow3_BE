package com.englow3.exam.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much content one paper actually holds. An interface rather than a record because the query behind it is native,
 * and a constructor projection is a JPQL-only feature - the aliases in that query are quoted so Postgres keeps their
 * case and Spring Data can match them to these getters.
 */
public interface ExamContentTotals {

    UUID getExamId();

    long getSectionCount();

    long getQuestionCount();

    BigDecimal getSectionsRawTotal();
}
