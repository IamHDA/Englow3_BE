package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.LearnerFixture;
import com.englow3.ai.api.AiJobQueue;

class TutorOperationsIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    AiJobQueue queue;
    @Autowired
    JdbcClient jdbc;

    @Test
    void operationsDistinguishMissingUsageFromZeroWithoutReturningPrivateContent() {
        UUID user = new LearnerFixture(jdbc).learner();
        UUID target = UUID.randomUUID();
        var before = queue.operations().metrics();
        queue.enqueueTutorReply(target, "{}", "metrics:" + target, "test", user);
        jdbc.sql(
                "update ai_jobs set status='SUCCEEDED',started_at=created_at+interval '1 second',completed_at=created_at+interval '2 seconds',output_payload=cast(:payload as jsonb) where target_id=:id")
                .param("payload", "{\"content\":\"Synthetic answer\",\"input_tokens\":null,\"output_tokens\":null}")
                .param("id", target).update();
        var after = queue.operations().metrics();
        assertThat(after.unknownUsageJobs()).isEqualTo(before.unknownUsageJobs() + 1);
        assertThat(after.knownInputTokens()).isEqualTo(before.knownInputTokens());
        assertThat(after.completionP95Ms()).isNotNull();
        assertThat(after.toString()).doesNotContain("Synthetic answer");
    }
}
