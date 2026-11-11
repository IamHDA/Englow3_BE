package com.englow3.exam.query;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ExamBlueprintQuery {
    private final JdbcClient jdbc;

    public record Part(UUID sectionId, String skill, int seconds, int orderNo, long questions, BigDecimal marks,
            boolean audio, boolean text, boolean choicesOnly) {
        public Part(UUID sectionId, String skill, int seconds, int orderNo, long questions, BigDecimal marks,
                boolean audio, boolean text) {
            this(sectionId, skill, seconds, orderNo, questions, marks, audio, text, true);
        }
    }

    public List<Part> load(UUID examId) {
        return jdbc
                .sql("""
                        select s.id,s.section_type,coalesce(s.time_limit_seconds,0) seconds,p.order_no,
                          count(q.id) questions,coalesce(sum(q.max_raw_score),0) marks,
                          coalesce(bool_and(coalesce(nullif(p.audio_object_key,''),nullif(qs.audio_object_key,'')) is not null),false) audio,
                          coalesce(bool_and(coalesce(nullif(p.content,''),nullif(qs.content,'')) is not null),false) text,
                          coalesce(bool_and(q.question_type='SINGLE_CHOICE'),false) choices_only
                        from exam_sections s left join section_parts p on p.exam_section_id=s.id
                        left join question_sets qs on qs.section_part_id=p.id left join questions q on q.question_set_id=qs.id
                        where s.exam_id=:id group by s.id,s.section_type,s.time_limit_seconds,p.id,p.order_no order by s.order_no,p.order_no
                        """)
                .param("id", examId)
                .query((r, n) -> new Part(r.getObject("id", UUID.class), r.getString("section_type"),
                        r.getInt("seconds"), r.getInt("order_no"), r.getLong("questions"), r.getBigDecimal("marks"),
                        r.getBoolean("audio"), r.getBoolean("text"), r.getBoolean("choices_only")))
                .list();
    }
}
