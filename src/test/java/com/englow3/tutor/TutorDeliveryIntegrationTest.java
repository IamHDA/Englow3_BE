package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.dto.result.TutorConversationResult;
import com.englow3.tutor.service.TutorReplyWriter;
import com.englow3.tutor.service.TutorService;

class TutorDeliveryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorService service;
    @Autowired
    TutorReplyWriter writer;
    @Autowired
    JdbcClient jdbc;
    private UUID user;

    @BeforeEach
    void setup() {
        user = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @AfterEach
    void cleanup() {
        SignedIn.out();
    }

    @Test
    void twentyConcurrentDeliveriesAcceptOneQuestionAndOneJob() throws Exception {
        UUID key = UUID.randomUUID();
        var command = new SendTutorMessageCommand(null, "Explain the present perfect.", null, key, "GENERAL", false);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<TutorConversationResult>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++)
                calls.add(() -> {
                    SignedIn.as(jdbc, user);
                    try {
                        return service.send(command);
                    } finally {
                        SignedIn.out();
                    }
                });
            var results = executor.invokeAll(calls);
            UUID id = results.getFirst().get().conversation().id();
            for (var result : results)
                assertThat(result.get().conversation().id()).isEqualTo(id);
            assertThat(service.conversation(id).messages()).hasSize(2);
            assertThat(jdbc.sql("select count(*) from ai_jobs where requested_by_user_id = :id").param("id", user)
                    .query(Long.class).single()).isEqualTo(1);
        }
    }

    @Test
    void aReusedKeyWithDifferentTextIsRejectedWithoutAnotherJob() {
        UUID key = UUID.randomUUID();
        service.send(new SendTutorMessageCommand(null, "Explain articles.", null, key, null, false));
        assertThatThrownBy(
                () -> service.send(new SendTutorMessageCommand(null, "Different text", null, key, null, false)))
                        .hasMessageContaining("different action");
        assertThat(jdbc.sql("select count(*) from ai_jobs where requested_by_user_id = :id").param("id", user)
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void cancelledAndOldRevisionCannotOverwriteARestart() {
        var first = service
                .send(new SendTutorMessageCommand(null, "Explain articles.", null, UUID.randomUUID(), null, false));
        UUID conversation = first.conversation().id(), answer = first.messages().get(1).id();
        service.cancel(conversation, answer);
        assertThat(service.reply(conversation, answer).status()).isEqualTo("CANCELLED");
        UUID key = UUID.randomUUID();
        var restarted = service.restart(conversation, answer, key, false);
        assertThat(restarted.messages().get(1).revision()).isEqualTo(1);
        service.restart(conversation, answer, key, false);
        writer.storeReply(answer, 0, "Stale answer", "test", 1, 1);
        assertThat(service.reply(conversation, answer).status()).isEqualTo("PENDING");
        writer.storeReply(answer, 1, "Use an before a vowel sound.", "test", 1, 1);
        assertThat(service.reply(conversation, answer).content()).contains("vowel");
        assertThat(jdbc.sql("select count(*) from ai_jobs where requested_by_user_id = :id").param("id", user)
                .query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void receiptAndMessagesNeverCrossAnAccountBoundary() {
        UUID key = UUID.randomUUID();
        var first = service.send(new SendTutorMessageCommand(null, "Explain articles.", null, key, null, false));
        SignedIn.as(jdbc, new LearnerFixture(jdbc).learner());
        assertThatThrownBy(() -> service.receipt(key)).hasMessageContaining("not been accepted");
        assertThatThrownBy(() -> service.cancel(first.conversation().id(), first.messages().get(1).id()))
                .hasMessageContaining("not found");
    }
}
