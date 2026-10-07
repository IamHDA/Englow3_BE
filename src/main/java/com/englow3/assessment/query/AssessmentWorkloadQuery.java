package com.englow3.assessment.query;

import java.util.UUID;
import com.englow3.assessment.dto.result.AssessmentWorkloadResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AssessmentWorkloadQuery {
    private final JdbcClient jdbcClient;

    public AssessmentWorkloadResult counts(UUID author) {
        return jdbcClient.sql("""
                with tasks as (select id, status from assessment_tasks
                    where :allAuthors or created_by_user_id = :author),
                attempts as (select a.status from assessment_attempts a join tasks t on t.id = a.task_id)
                select
                  (select count(*) from tasks where status = 'DRAFT') drafts,
                  (select count(*) from tasks where status = 'REJECTED') rejected,
                  (select count(*) from tasks where status = 'PENDING_REVIEW') pending,
                  (select count(*) from tasks where status = 'PUBLISHED') published,
                  (select count(*) from attempts where status = 'NEEDS_REVIEW') needs,
                  (select count(*) from attempts where status = 'FAILED') failed,
                  (select count(*) from attempts where status = 'COMPLETED') completed
                """).param("allAuthors", author == null).param("author", author == null ? new UUID(0, 0) : author)
                .query((rs, row) -> new AssessmentWorkloadResult(rs.getLong("drafts"), rs.getLong("rejected"),
                        rs.getLong("pending"), rs.getLong("published"), rs.getLong("needs"), rs.getLong("failed"),
                        rs.getLong("completed")))
                .single();
    }
}
