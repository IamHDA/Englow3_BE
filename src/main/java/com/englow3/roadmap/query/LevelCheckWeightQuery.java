package com.englow3.roadmap.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.roadmap.helper.LevelCombiner.Weights;
import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/** The level-check weights in force at a moment, for a learner's purposes. Reads the roadmap's own table. */
@Component
@RequiredArgsConstructor
public class LevelCheckWeightQuery {

    static final String DEFAULT_PURPOSE = "DEFAULT";

    private final JdbcClient jdbc;

    /**
     * The highest-priority row among {@code purposeCodes}, else the DEFAULT row - each as it stood at {@code at}. Empty
     * only if the table has no row that applies at all.
     */
    public Optional<Weights> weightsFor(Collection<String> purposeCodes, Instant at) {
        List<String> codes = new ArrayList<>(purposeCodes == null ? List.of() : purposeCodes);
        codes.add(DEFAULT_PURPOSE);
        return jdbc.sql("""
                select w.vocabulary, w.listening, w.speaking from level_check_weights w
                 where w.purpose_code in (:codes)
                   and w.effective_from = (select max(effective_from) from level_check_weights
                                            where purpose_code = w.purpose_code and effective_from <= :at)
                 order by (w.purpose_code = 'DEFAULT'), w.priority desc, w.purpose_code
                 limit 1
                """).param("codes", codes).param("at", SqlTime.at(at))
                .query((rs, row) -> new Weights(rs.getInt("vocabulary"), rs.getInt("listening"), rs.getInt("speaking")))
                .optional();
    }
}
