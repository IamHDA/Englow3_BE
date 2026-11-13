package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.*;
import com.englow3.tutor.entity.*;
import com.englow3.tutor.repository.*;
import com.englow3.tutor.service.*;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.fasterxml.jackson.databind.ObjectMapper;

class TutorMemoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorService tutor;
    @Autowired
    TutorReplyWriter writer;
    @Autowired
    TutorConversationRepository conversations;
    @Autowired
    TutorMessageRepository messages;
    @Autowired
    TutorMemoryRepository memories;
    @Autowired
    TutorDataService data;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired
    com.englow3.ai.api.AiJobQueue jobs;
    private UUID user, conversation, firstMessage;

    @BeforeEach
    void setup() {
        user = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(tutor, "enabled", true);
        ReflectionTestUtils.setField(tutor, "memoryEnabled", true);
        var thread = conversations
                .saveAndFlush(TutorConversation.start(user, "Long synthetic lesson", "GENERAL", Instant.now()));
        conversation = thread.getId();
        var rows = new ArrayList<TutorMessage>();
        for (int order = 1; order <= 30; order++) {
            TutorMessage row;
            if (order % 2 == 1)
                row = TutorMessage.fromLearner(conversation, order, "Synthetic lesson " + order);
            else {
                row = TutorMessage.awaitingReply(conversation, order);
                row.answer("Synthetic explanation " + order, "test", "test", 1, 1, Instant.now());
            }
            if (order == 2)
                firstMessage = row.getId();
            rows.add(row);
            thread.recordTurn(Instant.now());
        }
        messages.saveAllAndFlush(rows);
        conversations.saveAndFlush(thread);
    }

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(tutor, "enabled", false);
        ReflectionTestUtils.setField(tutor, "memoryEnabled", false);
        SignedIn.out();
    }

    private com.fasterxml.jackson.databind.JsonNode payload(UUID answer) throws Exception {
        return new ObjectMapper().readTree(jdbc.sql("select input_payload::text from ai_jobs where target_id=:id")
                .param("id", answer).query(String.class).single());
    }

    private void finish(UUID answer, String text) throws Exception {
        var request = payload(answer);
        writer.storeSummaryReply(answer, 0, request.path("memoryEpoch").asLong(),
                request.path("memoryThroughOrder").asInt(), text, "test", 10, 20);
    }

    @Test
    void aBoundedSummaryUsesOneDurableRequestAndBecomesUnverifiedContext() throws Exception {
        UUID key = UUID.randomUUID();
        var accepted = tutor.summarize(conversation, key);
        assertThat(accepted.messages()).allSatisfy(message -> assertThat(message.createdAt()).isNotNull());
        UUID answer = accepted.messages().get(1).id();
        for (int duplicate = 0; duplicate < 20; duplicate++)
            assertThat(tutor.summarize(conversation, key).messages().get(1).id()).isEqualTo(answer);
        assertThat(jdbc.sql("select count(*) from ai_jobs where target_id=:id").param("id", answer).query(Long.class)
                .single()).isEqualTo(1);
        assertThat(payload(answer).path("memoryThroughOrder").asInt()).isEqualTo(20);
        finish(answer, "Notes: practise past tense and ask for short examples.");
        var memory = tutor.memory(conversation);
        assertThat(memory.content()).contains("past tense");
        assertThat(memory.remainingMessages()).isEqualTo(10);
        assertThat(messages.findById(answer).orElseThrow().getPromptVersion()).isEqualTo(TutorMemoryPrompt.VERSION);
        var followup = tutor.send(new SendTutorMessageCommand(conversation, "Continue practising", null,
                UUID.randomUUID(), "GENERAL", false));
        var input = payload(followup.messages().get(1).id());
        assertThat(input.path("systemPrompt").asText()).doesNotContain("Notes: practise");
        assertThat(input.path("messages").toString()).contains("Unverified AI conversation notes", "past tense")
                .doesNotContain("Synthetic lesson 1\"");
        assertThatThrownBy(() -> tutor.restart(conversation, answer, UUID.randomUUID(), true))
                .hasMessageContaining("memory controls");
    }

    @Test
    void clearAndApprovedCorrectionsFenceLateSummaryCompletion() throws Exception {
        var first = tutor.summarize(conversation, UUID.randomUUID());
        UUID answer = first.messages().get(1).id();
        tutor.clearMemory(conversation);
        finish(answer, "Old notes after clear");
        assertThat(tutor.memory(conversation).content()).isNull();
        var second = tutor.summarize(conversation, UUID.randomUUID());
        UUID secondAnswer = second.messages().get(1).id();
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(tx -> memories.invalidateForMessage(firstMessage));
        finish(secondAnswer, "Old notes after correction");
        assertThat(tutor.memory(conversation).content()).isNull();
    }

    @Test
    void correctionRacingWithTheFirstSummaryCannotLeaveStaleMemory() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var corrected = new java.util.concurrent.CountDownLatch(1);
        try {
            var summary = pool.submit(() -> {
                SignedIn.as(jdbc, user);
                try {
                    return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(tx -> {
                        jobs.lockBudget(user);
                        conversations.lockOwned(conversation, user).orElseThrow();
                        memories.lock(conversation);
                        locked.countDown();
                        await(corrected);
                        return tutor.summarize(conversation, UUID.randomUUID());
                    });
                } finally {
                    SignedIn.out();
                }
            });
            var correction = pool.submit(() -> {
                await(locked);
                new org.springframework.transaction.support.TransactionTemplate(transactions)
                        .executeWithoutResult(tx -> {
                            jdbc.sql(
                                    "update tutor_messages set correction='Approved correction: use since for a starting point',report_status='RESOLVED' where id=:id")
                                    .param("id", firstMessage).update();
                            corrected.countDown();
                            memories.invalidateForMessage(firstMessage);
                        });
            });
            var accepted = summary.get(15, java.util.concurrent.TimeUnit.SECONDS);
            correction.get(15, java.util.concurrent.TimeUnit.SECONDS);
            finish(accepted.messages().get(1).id(), "Old summary produced while correction was being approved");
            assertThat(tutor.memory(conversation).content()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("Concurrent test step timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void memoryIsOwnerScopedExportedAndRemovedWithTutorData() throws Exception {
        UUID answer = tutor.summarize(conversation, UUID.randomUUID()).messages().get(1).id();
        finish(answer, "Synthetic study notes");
        assertThat(data.export("MEMORY", 0).items())
                .anyMatch(row -> row.path("content").asText().contains("Synthetic study notes"));
        SignedIn.as(jdbc, new LearnerFixture(jdbc).learner());
        assertThatThrownBy(() -> tutor.memory(conversation)).hasMessageContaining("not found");
        SignedIn.as(jdbc, user);
        data.purge();
        assertThat(memories.read(conversation).content()).isNull();
        assertThat(jdbc.sql("select count(*) from tutor_memories where conversation_id=:id").param("id", conversation)
                .query(Long.class).single()).isZero();
    }
}
