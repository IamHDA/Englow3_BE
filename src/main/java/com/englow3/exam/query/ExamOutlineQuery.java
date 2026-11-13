package com.englow3.exam.query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.englow3.exam.entity.SectionType;

import lombok.RequiredArgsConstructor;

/**
 * A paper's parts with how many questions and points each holds - what a learner picks a practice from, and what a
 * practice is totalled over. One query with the counts done in the database, rather than loading every question to
 * count it.
 */
@Repository
@RequiredArgsConstructor
public class ExamOutlineQuery {

    private final JdbcClient jdbc;

    public record OutlinePart(UUID id, UUID sectionId, SectionType sectionType, int sectionOrderNo, int orderNo,
            String title, long questionCount, BigDecimal maxRawScore) {
    }

    /** Every part of the paper, in paper order, including parts that hold no questions yet. */
    public List<OutlinePart> load(UUID examId) {
        return jdbc.sql("""
                select p.id, s.id as section_id, s.section_type, s.order_no as section_order_no, p.order_no,
                       p.title, count(q.id) as question_count, coalesce(sum(q.max_raw_score), 0) as max_raw_score
                  from section_parts p
                  join exam_sections s on s.id = p.exam_section_id
                  left join question_sets qs on qs.section_part_id = p.id
                  left join questions q on q.question_set_id = qs.id
                 where s.exam_id = :examId
                 group by p.id, s.id, s.section_type, s.order_no, p.order_no, p.title
                 order by s.order_no, p.order_no
                """).param("examId", examId)
                .query((rs, rowNum) -> new OutlinePart(rs.getObject("id", UUID.class),
                        rs.getObject("section_id", UUID.class), SectionType.valueOf(rs.getString("section_type")),
                        rs.getInt("section_order_no"), rs.getInt("order_no"), rs.getString("title"),
                        rs.getLong("question_count"), rs.getBigDecimal("max_raw_score")))
                .list();
    }

    /** Titles and skills of these parts, whichever papers they belong to - for labelling practices in history. */
    public Map<UUID, OutlinePart> describe(Collection<UUID> partIds) {
        if (partIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                select p.id, s.id as section_id, s.section_type, s.order_no as section_order_no, p.order_no, p.title
                  from section_parts p
                  join exam_sections s on s.id = p.exam_section_id
                 where p.id in (:partIds)
                """).param("partIds", partIds)
                .query((rs, rowNum) -> new OutlinePart(rs.getObject("id", UUID.class),
                        rs.getObject("section_id", UUID.class), SectionType.valueOf(rs.getString("section_type")),
                        rs.getInt("section_order_no"), rs.getInt("order_no"), rs.getString("title"), 0,
                        BigDecimal.ZERO))
                .list().stream().collect(Collectors.toMap(OutlinePart::id, Function.identity()));
    }
}
