package com.englow3.shared.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Turns an {@link Instant} into something the PostgreSQL driver will bind.
 * <p>
 * The driver refuses {@code java.time.Instant} outright - "Can't infer the SQL type to use" - because an instant names
 * a point in time with no offset attached, and the driver will not pick one for you. JPA never hits this: Hibernate
 * knows the column type from the mapping and converts before binding. The {@code query/} read models talk to
 * {@code JdbcClient} directly, where there is no mapping to consult, so the conversion has to happen here.
 * <p>
 * UTC, to match the columns: every {@code timestamptz} in this schema is written and compared in UTC, and the streak
 * and daily aggregates already cast to a UTC date. Binding in any other offset would move rows across day boundaries
 * for learners nowhere near that offset.
 */
public final class SqlTime {

    private SqlTime() {
    }

    public static OffsetDateTime at(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Reads a {@code timestamptz} back.
     * <p>
     * The refusal runs both ways - {@code getObject(column, Instant.class)} fails with "conversion to class
     * java.time.Instant from timestamptz not supported" - so a read goes through {@link OffsetDateTime} the same way a
     * write does. Null stays null: a column that has not been set is not the epoch.
     */
    public static Instant read(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
