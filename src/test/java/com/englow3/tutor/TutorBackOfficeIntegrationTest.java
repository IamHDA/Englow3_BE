package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.*;
import com.englow3.tutor.dto.command.*;
import com.englow3.tutor.service.*;

class TutorBackOfficeIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorBackOfficeService backOffice;
    @Autowired
    TutorKnowledgeService knowledge;
    @Autowired
    TutorService tutor;
    @Autowired
    TutorReplyWriter writer;
    @Autowired
    JdbcClient jdbc;
    private UUID admin, staff, learner;

    @BeforeEach
    void setup() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        admin = fixture.learner();
        staff = fixture.learner();
        learner = fixture.learner();
        jdbc.sql("update users set role='ADMIN' where id=:id").param("id", admin).update();
        jdbc.sql("update users set role='STAFF' where id=:id").param("id", staff).update();
        ReflectionTestUtils.setField(tutor, "enabled", true);
    }

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(tutor, "enabled", false);
        SignedIn.out();
    }

    private void as(UUID id, String role) {
        SignedIn.as(jdbc, id);
        var token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token.getToken(), List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void onlyPublishedResourcesAreRetrievedAndWithdrawalTakesEffect() {
        String term = "grammar" + UUID.randomUUID().toString().replace("-", "");
        as(staff, "STAFF");
        var draft = backOffice.create(new TutorResourceCommand(term, "Use an before a vowel sound.", 0));
        assertThat(knowledge.retrieve(term)).isEmpty();
        backOffice.resourceAction(draft.id(), new TutorReviewCommand("SUBMIT", null, null, null));
        assertThatThrownBy(
                () -> backOffice.resourceAction(draft.id(), new TutorReviewCommand("PUBLISH", null, null, null)))
                        .hasMessageContaining("Administrator");
        as(admin, "ADMIN");
        var published = backOffice.resourceAction(draft.id(), new TutorReviewCommand("PUBLISH", null, null, null));
        assertThat(knowledge.retrieve(term)).extracting(source -> source.resourceId()).containsExactly(draft.id());
        assertThatThrownBy(() -> backOffice.update(draft.id(),
                new TutorResourceCommand("Bad edit", "Changed", published.rowVersion())))
                        .hasMessageContaining("Refresh");
        backOffice.resourceAction(draft.id(), new TutorReviewCommand("ARCHIVE", null, "Superseded explanation", null));
        assertThat(knowledge.retrieve(term)).isEmpty();
    }

    @Test
    void staffOnlyReadsAssignedReportsAndApprovedCorrectionReachesLearner() {
        as(learner, "LEARNER");
        var sent = tutor
                .send(new SendTutorMessageCommand(null, "Explain articles", null, UUID.randomUUID(), "GENERAL", false));
        assertThat(sent.conversation().createdAt()).isNotNull();
        assertThat(sent.messages().getFirst().createdAt()).isNotNull();
        UUID conversation = sent.conversation().id(), answer = sent.messages().get(1).id();
        writer.storeReply(answer, 0, "An is used before every word beginning with a vowel letter.", "test", 10, 20);
        jdbc.sql(
                "insert into tutor_memories(conversation_id,content,through_order) values(:id,'Old rule inferred from this answer',2)")
                .param("id", conversation).update();
        tutor.report(conversation, answer, new ReportTutorMessageCommand("Vowel sound, not necessarily vowel letter."));
        as(admin, "ADMIN");
        var report = backOffice.reports("OPEN", PageRequest.of(0, 100)).getContent().stream()
                .filter(r -> r.messageId().equals(answer)).findFirst().orElseThrow();
        as(staff, "STAFF");
        assertThat(backOffice.reports("", PageRequest.of(0, 100))).isEmpty();
        assertThatThrownBy(() -> backOffice.report(report.id())).hasMessageContaining("not found");
        as(admin, "ADMIN");
        backOffice.reportAction(report.id(), new TutorReviewCommand("ASSIGN", staff, null, null));
        as(staff, "STAFF");
        assertThat(backOffice.report(report.id()).question()).contains("articles");
        backOffice.reportAction(report.id(), new TutorReviewCommand("PROPOSE", null, "Sound determines the article.",
                "Use an before a vowel sound; a university uses a."));
        assertThatThrownBy(
                () -> backOffice.reportAction(report.id(), new TutorReviewCommand("RESOLVE", null, "Approved", null)))
                        .hasMessageContaining("Administrator");
        as(admin, "ADMIN");
        backOffice.reportAction(report.id(),
                new TutorReviewCommand("RESOLVE", null, "Correction reviewed and approved.", null));
        as(learner, "LEARNER");
        var reply = tutor.reply(conversation, answer);
        assertThat(reply.reportStatus()).isEqualTo("RESOLVED");
        assertThat(reply.correction()).contains("vowel sound");
        assertThat(jdbc.sql("select content is null from tutor_memories where conversation_id=:id")
                .param("id", conversation).query(Boolean.class).single()).isTrue();
        assertThat(reply.content()).contains("vowel letter");
    }

    @Test
    void aLearnerCannotReadBackOfficeContentOrReports() {
        as(learner, "LEARNER");
        assertThatThrownBy(() -> backOffice.resources("", PageRequest.of(0, 15))).hasMessageContaining("Back-office");
        assertThatThrownBy(() -> backOffice.reports("", PageRequest.of(0, 15))).hasMessageContaining("Back-office");
    }

    @Test
    void approvalAfterRegenerationRemainsAttachedToTheOriginalAnswerVersion() {
        as(learner, "LEARNER");
        var sent = tutor
                .send(new SendTutorMessageCommand(null, "Explain articles", null, UUID.randomUUID(), "GENERAL", false));
        UUID conversation = sent.conversation().id(), answer = sent.messages().get(1).id();
        writer.storeReply(answer, 0, "Use an before every vowel letter.", "test", 1, 1);
        tutor.report(conversation, answer, new ReportTutorMessageCommand("Please check the rule."));
        tutor.restart(conversation, answer, UUID.randomUUID(), true);
        writer.storeReply(answer, 1, "A different explanation.", "test", 1, 1);
        as(admin, "ADMIN");
        var ticket = backOffice.reports("OPEN", PageRequest.of(0, 100)).getContent().stream()
                .filter(r -> r.messageId().equals(answer)).findFirst().orElseThrow();
        backOffice.reportAction(ticket.id(), new TutorReviewCommand("ASSIGN", staff, null, null));
        as(staff, "STAFF");
        backOffice.reportAction(ticket.id(),
                new TutorReviewCommand("PROPOSE", null, "Check vowel sounds.", "Use an before a vowel sound."));
        as(admin, "ADMIN");
        backOffice.reportAction(ticket.id(),
                new TutorReviewCommand("RESOLVE", null, "Approved after checking examples.", null));
        as(learner, "LEARNER");
        assertThat(tutor.reply(conversation, answer).content()).isEqualTo("A different explanation.");
        assertThat(tutor.reply(conversation, answer).correction()).isNull();
        assertThat(tutor.replyHistory(conversation, answer).items().getFirst().correction()).contains("vowel sound");
        tutor.selectReply(conversation, answer, 0, UUID.randomUUID());
        assertThat(tutor.reply(conversation, answer).content()).contains("vowel letter");
        assertThat(tutor.reply(conversation, answer).correction()).contains("vowel sound");
    }

}
