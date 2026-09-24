package com.englow3.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;
import com.englow3.ai.service.AiJobQueue;
import com.englow3.ai.worker.AiJobWorker;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.service.TutorReplyHandler;
import com.englow3.tutor.service.TutorReplyWriter;
import com.englow3.tutor.service.TutorService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A question asked, queued, answered and read back, against a real database.
 * <p>
 * Every step here was unit-tested and none of it had ever run end to end. When it first did, the queue could not store
 * a job at all - its payload column is {@code jsonb} and the entity bound a {@code varchar} - so no tutor question and
 * no speaking submission could ever have been queued. Only the provider is faked: there is no key, and the point is
 * everything on this side of the call.
 * <p>
 * The worker is not started - it is off in tests, and a scheduled drain would race these assertions - so the test does
 * what one pass of it does: claim, run the handler, record.
 */
class TutorPipelineIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private TutorService tutorService;

    @Autowired
    private TutorReplyWriter writer;

    @Autowired
    private AiJobQueue queue;

    @Autowired
    private JdbcClient jdbc;

    private final LlmClient llm = mock(LlmClient.class);

    private TutorReplyHandler handler;

    @BeforeEach
    void setUp() {
        SignedIn.as(jdbc, new LearnerFixture(jdbc).learner());
        handler = new TutorReplyHandler(llm, writer, new ObjectMapper());
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    /**
     * One pass of the real worker. It claims every pending job, not only this test's - harmless, since each test
     * asserts only on its own learner's rows, and the only handler registered is the tutor one.
     */
    private void drain(UUID pendingMessageId) {
        new AiJobWorker(queue, List.of(handler), 100, Duration.ofMinutes(5)).drain();
    }

    @Test
    void answersAQuestionAndShowsTheAnswer() {
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn(
                "{\"content\":\"A gerund is a verb used as a noun.\",\"model\":\"test-model\",\"input_tokens\":12,\"output_tokens\":9}");

        var sent = tutorService.send(new SendTutorMessageCommand(null, "What is a gerund?", "grammar"));
        UUID conversationId = sent.conversation().id();
        UUID pending = sent.messages().get(1).id();
        assertThat(sent.messages().get(1).status()).isEqualTo("PENDING");

        drain(pending);

        var thread = tutorService.conversation(conversationId);
        assertThat(thread.messages()).hasSize(2);
        assertThat(thread.messages().get(1).status()).isEqualTo("READY");
        assertThat(thread.messages().get(1).content()).isEqualTo("A gerund is a verb used as a noun.");
        assertThat(thread.messages().get(1).model()).isEqualTo("test-model");
    }

    /** The second question travels with the first exchange, and lands after it in the transcript. */
    @Test
    void continuesAThreadInOrder() {
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"content\":\"First answer.\"}").thenReturn("{\"content\":\"Second answer.\"}");

        var first = tutorService.send(new SendTutorMessageCommand(null, "What is a gerund?", null));
        drain(first.messages().get(1).id());
        var second = tutorService
                .send(new SendTutorMessageCommand(first.conversation().id(), "Give me an example.", null));
        drain(second.messages().get(1).id());

        var thread = tutorService.conversation(first.conversation().id());
        assertThat(thread.messages()).extracting(message -> message.orderNo()).containsExactly(1, 2, 3, 4);
        assertThat(thread.messages().get(3).content()).isEqualTo("Second answer.");
        assertThat(thread.conversation().messageCount()).isEqualTo(4);
    }

    /**
     * A provider that is down leaves the turn pending, not failed. The learner is told nothing untrue, and the job goes
     * back in the queue for the retry that may well succeed.
     */
    @Test
    void leavesTheTurnWaitingWhenTheProviderIsDown() {
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new LlmException("TUTOR_SERVICE_UNREACHABLE", "timeout", true));

        var sent = tutorService.send(new SendTutorMessageCommand(null, "What is a gerund?", null));
        drain(sent.messages().get(1).id());

        var thread = tutorService.conversation(sent.conversation().id());
        assertThat(thread.messages().get(1).status()).isEqualTo("PENDING");
        String jobStatus = jdbc.sql("select status from ai_jobs where target_id = :target")
                .param("target", sent.messages().get(1).id()).query(String.class).single();
        assertThat(jobStatus).isEqualTo("PENDING");
    }

    /** A final refusal is the one failure the learner is shown, with the reason attached. */
    @Test
    void marksTheTurnFailedWhenNoRetryIsComing() {
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new LlmException("TUTOR_GENERATION_REJECTED", "400", false));

        var sent = tutorService.send(new SendTutorMessageCommand(null, "What is a gerund?", null));
        drain(sent.messages().get(1).id());

        var reply = tutorService.conversation(sent.conversation().id()).messages().get(1);
        assertThat(reply.status()).isEqualTo("FAILED");
        assertThat(reply.errorCode()).isEqualTo("TUTOR_GENERATION_REJECTED");
    }

    /**
     * "Still waiting" is only true while a retry is coming. Once the last one fails the job is finished, and a turn
     * left pending would have the learner wait for an answer that will never arrive.
     */
    @Test
    void tellsTheLearnerOnceTheLastRetryHasFailed() {
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new LlmException("TUTOR_SERVICE_UNREACHABLE", "timeout", true));

        var sent = tutorService.send(new SendTutorMessageCommand(null, "What is a gerund?", null));
        UUID pending = sent.messages().get(1).id();
        for (int attempt = 0; attempt < 3; attempt++) {
            // Due immediately, so the backoff does not hold the job out of the next claim.
            jdbc.sql("update ai_jobs set next_retry_at = null where target_id = :target").param("target", pending)
                    .update();
            drain(pending);
        }

        String jobStatus = jdbc.sql("select status from ai_jobs where target_id = :target").param("target", pending)
                .query(String.class).single();
        var reply = tutorService.conversation(sent.conversation().id()).messages().get(1);
        assertThat(jobStatus).isEqualTo("FAILED");
        assertThat(reply.status()).isEqualTo("FAILED");
        assertThat(reply.errorCode()).isEqualTo("TUTOR_SERVICE_UNREACHABLE");
    }
}
