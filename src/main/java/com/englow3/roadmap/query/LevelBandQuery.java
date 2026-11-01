package com.englow3.roadmap.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/** A level-check section's share of points as a CEFR band, by the conversion in force when it was scored. */
@Component
@RequiredArgsConstructor
public class LevelBandQuery {

    private final JdbcClient jdbc;

    public Optional<CefrLevel> band(String sectionType, BigDecimal percent, Instant scoredAt) {
        return jdbc.sql("""
                select cefr_level from level_check_conversions c
                 where c.section_type = :section and c.min_percent <= :percent
                   and c.effective_from = (select max(effective_from) from level_check_conversions
                                            where section_type = :section and effective_from <= :at)
                 order by c.min_percent desc
                 limit 1
                """).param("section", sectionType).param("percent", percent).param("at", SqlTime.at(scoredAt))
                .query(String.class).optional().flatMap(CefrLevel::parse);
    }
}
