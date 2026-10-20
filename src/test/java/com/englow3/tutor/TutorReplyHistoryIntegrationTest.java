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
import com.englow3.tutor.service.TutorService;
import com.englow3.tutor.service.TutorReplyWriter;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.ai.api.AiJobQueue;

class TutorReplyHistoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorService tutor;
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
    void selectingAnEarlierAnswerIsDurableIdempotentAndDoesNotSpendAnotherRequest() {
        UUID user = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(tutor, "enabled", true);
        var first = tutor.send(
                new SendTutorMessageCommand(null, "Explain articles.", null, UUID.randomUUID(), "GENERAL", false));
        UUID conversation = first.conversation().id(), answer = first.messages().get(1).id();
        writer.storeReply(answer, 0, "First answer.", "test", 3, 2);
        tutor.restart(conversation, answer, UUID.randomUUID(), true);
        writer.storeReply(answer, 1, "Second answer.", "test", 3, 2);
        UUID key = UUID.randomUUID();
        tutor.selectReply(conversation, answer, 0, key);
        tutor.selectReply(conversation, answer, 0, key);
        assertThat(tutor.reply(conversation, answer).content()).isEqualTo("First answer.");
        assertThat(tutor.reply(conversation, answer).restartCount()).isEqualTo(1);
        assertThat(tutor.reply(conversation, answer).revision()).isEqualTo(2);
        assertThat(jobs.usage(user).used()).isEqualTo(2);
        assertThat(tutor.replyHistory(conversation, answer).items()).hasSize(3);
        tutor.restart(conversation, answer, UUID.randomUUID(), true);
        tutor.cancel(conversation, answer);
        tutor.selectReply(conversation, answer, 0, UUID.randomUUID());
        assertThat(tutor.reply(conversation, answer).status()).isEqualTo("READY");
        assertThat(tutor.reply(conversation, answer).content()).isEqualTo("First answer.");
        assertThat(tutor.reply(conversation, answer).restartCount()).isEqualTo(2);
    }

    @Test
    void editingAnEarlierQuestionBranchesWithoutChangingTheOriginalOrCrossingOwnership() {
        UUID user = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(tutor, "enabled", true);
        var first = tutor.send(
                new SendTutorMessageCommand(null, "Explain articles.", null, UUID.randomUUID(), "GENERAL", false));
        writer.storeReply(first.messages().get(1).id(), 0, "First answer.", "test", 3, 2);
        var second = tutor.send(new SendTutorMessageCommand(first.conversation().id(), "Give two examples.", null,
                UUID.randomUUID(), "GENERAL", false));
        UUID key = UUID.randomUUID();
        var branched = tutor.branch(first.conversation().id(), second.messages().get(0).id(),
                "Give three examples instead.", key, false);
        assertThat(branched.conversation().id()).isNotEqualTo(first.conversation().id());
        assertThat(branched.messages()).hasSize(4);
        assertThat(branched.messages().get(2).content()).isEqualTo("Give three examples instead.");
        assertThat(tutor.conversation(first.conversation().id()).messages().get(2).content())
                .isEqualTo("Give two examples.");
        assertThat(tutor.branch(first.conversation().id(), second.messages().get(0).id(),
                "Give three examples instead.", key, false).conversation().id())
                        .isEqualTo(branched.conversation().id());
        SignedIn.as(jdbc, new LearnerFixture(jdbc).learner());
        assertThatThrownBy(() -> tutor.replyHistory(first.conversation().id(), first.messages().get(1).id()))
                .hasMessageContaining("not found");
    }
}
