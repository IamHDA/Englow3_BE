package com.englow3.ai.tutor;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class TutorGroundingService {

    private final JdbcTemplate jdbcTemplate;

    TutorGroundingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<GroundingReference> findApprovedContext(String query) {
        String pattern = "%" + query.strip().replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbcTemplate.query("""
                select content_type, content_id, label, body from (
                    select 'FLASHCARD' as content_type, id as content_id, lemma as label,
                           definition_en || E'\n' || definition_vi as body
                    from flashcards
                    where review_status in ('approved', 'published', 'human_verified')
                      and (lemma ilike ? escape '\\' or definition_en ilike ? escape '\\')
                    union all
                    select 'GRAMMAR' as content_type, id as content_id, title_en as label,
                           theory_en_summary || E'\n' || theory_vi as body
                    from grammar_points
                    where review_status in ('approved', 'published', 'human_verified')
                      and (title_en ilike ? escape '\\' or theory_en_summary ilike ? escape '\\')
                ) approved_content
                limit 5
                """,
                (resultSet, row) -> new GroundingReference(resultSet.getString("content_type"),
                        resultSet.getString("content_id"), resultSet.getString("label"), resultSet.getString("body")),
                pattern, pattern, pattern, pattern);
    }
}
