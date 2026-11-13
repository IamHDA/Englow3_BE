package com.englow3.assessment.query;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.assessment.dto.result.AssessmentSubmissionSummary;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;

import lombok.RequiredArgsConstructor;

/** Declared read-only reporting join to user identity; assessment writes only its own tables. */
@Component
@RequiredArgsConstructor
public class AssessmentQueueQuery {
    private final JdbcClient jdbcClient;

    public Page<AssessmentSubmissionSummary> search(UUID author, AssessmentAttemptStatus status, AssessmentSkill skill,
            String term, boolean oldest, Pageable requested) {
        Pageable page = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), 50));
        String search = term == null ? "" : term.strip().toLowerCase(Locale.ROOT);
        Map<String, Object> params = Map.of("allAuthors", author == null, "author",
                author == null ? new UUID(0, 0) : author, "status", status == null ? "" : status.name(), "skill",
                skill == null ? "" : skill.name(), "term",
                "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        String from = """
                from assessment_attempts a join assessment_tasks t on a.task_id = t.id join users u on a.user_id = u.id
                where a.status <> 'DRAFT' and (:allAuthors or t.created_by_user_id = :author)
                  and (:status = '' or a.status = :status) and (:skill = '' or a.skill = :skill)
                  and (lower(t.title) like :term or lower(u.display_name) like :term
                    or cast(a.id as text) like :term or cast(a.user_id as text) like :term)
                """;
        long count = jdbcClient.sql("select count(*) " + from).params(params).query(Long.class).single();
        List<AssessmentSubmissionSummary> items = jdbcClient.sql(
                "select a.id,a.task_id,a.skill,a.status,a.submitted_at,a.version,a.user_id,u.display_name,a.task_snapshot->>'title' as title "
                        + from + " order by a.submitted_at " + (oldest ? "asc" : "desc")
                        + ", a.id limit :limit offset :offset")
                .params(params).param("limit", page.getPageSize()).param("offset", page.getOffset())
                .query((rs, row) -> new AssessmentSubmissionSummary(rs.getObject("id", UUID.class),
                        AssessmentSkill.valueOf(rs.getString("skill")),
                        AssessmentAttemptStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("submitted_at").toInstant(), rs.getLong("version"),
                        rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                        new AssessmentSubmissionSummary.Task(rs.getObject("task_id", UUID.class),
                                rs.getString("title"))))
                .list();
        return new PageImpl<>(items, page, count);
    }
}
