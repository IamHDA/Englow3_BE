package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.LearnerFixture;
import com.englow3.support.SignedIn;
import com.englow3.tutor.service.TutorDataService;
import com.englow3.tutor.service.TutorService;
import com.englow3.tutor.service.TutorReplyWriter;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.ai.api.AiJobQueue;

class TutorDataIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorService tutor;
    @Autowired
    TutorDataService data;
    @Autowired
    TutorReplyWriter writer;
    @Autowired
    AiJobQueue jobs;
    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void cleanup() {
        SignedIn.out();
    }

    @Test
    void exportIsOwnedAndPurgeFencesWorkersScrubsJobsAndPreventsOutboxResurrection() {
        var fixture = new LearnerFixture(jdbc);
        UUID user = fixture.learner(), other = fixture.learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(tutor, "enabled", true);
        UUID key = UUID.randomUUID();
        var command = new SendTutorMessageCommand(null, "Explain articles.", null, key, "GENERAL", false);
        var sent = tutor.send(command);
        assertThat(data.export("MESSAGES", 0).items()).hasSize(2);
        SignedIn.as(jdbc, other);
        assertThat(data.export("MESSAGES", 0).items()).isEmpty();
        SignedIn.as(jdbc, user);
        data.purge();
        writer.storeReply(sent.messages().get(1).id(), 0, "Late provider completion", "test", 1, 1);
        assertThat(data.export("CONVERSATIONS", 0).items()).isEmpty();
        assertThat(data.export("MESSAGES", 0).items()).isEmpty();
        assertThat(jobs.usage(user).used()).isEqualTo(1);
        assertThatThrownBy(() -> tutor.send(command)).hasMessageContaining("deleted Tutor data");
        assertThat(
                jdbc.sql("select count(*) from ai_jobs where requested_by_user_id=:user and input_payload<>'{}'::jsonb")
                        .param("user", user).query(Long.class).single()).isZero();
    }
}
