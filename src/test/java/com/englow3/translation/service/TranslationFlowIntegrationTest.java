package com.englow3.translation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.shared.error.NotFoundException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.englow3.translation.dto.command.SaveTranslationSetCommand;
import com.englow3.translation.dto.command.SubmitTranslationCommand;
import com.englow3.translation.entity.TranslationAttemptStatus;
import com.englow3.translation.helper.TranslationRubric;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The whole loop against the real schema: a set authored and published, a learner translating a sentence, the grading
 * job queued, and the grade written back. The provider is not called - the worker is off in tests - so the grade is
 * written the way the handler's completion writes it.
 */
class TranslationFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    AdminTranslationService authoring;
    @Autowired
    TranslationService translation;
    @Autowired
    TranslationGradeWriter writer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcClient jdbc;

    UUID staff;
    UUID learner;
    UUID other;
    UUID setId;

    @BeforeEach
    void setup() {
        var fixture = new LearnerFixture(jdbc);
        staff = fixture.learner();
        learner = fixture.learner();
        other = fixture.learner();
        SignedIn.as(jdbc, staff);
        String slug = "translation-" + UUID.randomUUID();
        setId = authoring.saveAuthoring(new SaveTranslationSetCommand(null, null,
                new SaveTranslationSetCommand.Metadata(slug, "Daily life " + slug, "daily-life", "A2", null),
                List.of(new SaveTranslationSetCommand.Item("Tôi đã sống ở đây ba năm.",
                        "I have lived here for three years.", "Dùng hiện tại hoàn thành."),
                        new SaveTranslationSetCommand.Item("Trời đang mưa.", "It is raining.", null))))
                .id();
    }

    @AfterEach
    void cleanup() {
        SignedIn.out();
        for (UUID user : List.of(staff, learner, other)) {
            jdbc.sql("delete from ai_jobs where requested_by_user_id = :u").param("u", user).update();
        }
        jdbc.sql("delete from translation_attempts where translation_set_id = :s").param("s", setId).update();
        jdbc.sql("delete from translation_sets where id = :s").param("s", setId).update();
        for (UUID user : List.of(staff, learner, other)) {
            jdbc.sql("delete from users where id = :u").param("u", user).update();
        }
    }

    @Test
    void aDraftSetIsInvisibleToLearners() {
        UUID itemId = firstItemAsLearner();

        assertThatThrownBy(() -> translation.setDetail(setId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> translation.submit(new SubmitTranslationCommand(itemId, "I live here.")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aLearnerTranslatesAndReceivesAGrade() throws Exception {
        publish();
        SignedIn.as(jdbc, learner);
        var detail = translation.setDetail(setId);
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().getFirst().latestAttempt()).isNull();
        UUID itemId = detail.items().getFirst().id();

        var submitted = translation.submit(new SubmitTranslationCommand(itemId, "I live here three years."));
        assertThat(submitted.status()).isEqualTo(TranslationAttemptStatus.QUEUED);
        // The model answer stays hidden until the learner's own has been graded.
        assertThat(submitted.referenceEn()).isNull();
        assertThat(jdbc.sql("""
                select count(*) from ai_jobs
                where job_type = 'TRANSLATION_ASSESSMENT' and target_id = :id and requested_by_user_id = :u
                """).param("id", submitted.id()).param("u", learner).query(Long.class).single()).isEqualTo(1L);

        writer.storeGrade(submitted.id(), TranslationRubric.validate(mapper, """
                {"meaning":30,"grammar":15,"vocabulary":18,"naturalness":5,
                 "summary":"Đúng ý chính nhưng sai thì.","improved":"I have lived here for three years.",
                 "errors":[{"type":"GRAMMAR","original":"I live here","correction":"I have lived here",
                            "explanation":"Hành động kéo dài tới hiện tại dùng hiện tại hoàn thành."}]}
                """));

        var graded = translation.attempt(submitted.id());
        assertThat(graded.status()).isEqualTo(TranslationAttemptStatus.GRADED);
        assertThat(graded.score()).isEqualTo(68);
        assertThat(graded.feedback().verdict()).isEqualTo("GOOD");
        assertThat(graded.feedback().errors()).singleElement()
                .satisfies(error -> assertThat(error.correction()).isEqualTo("I have lived here"));
        assertThat(graded.referenceEn()).isEqualTo("I have lived here for three years.");
        assertThat(graded.notes()).isEqualTo("Dùng hiện tại hoàn thành.");

        var after = translation.setDetail(setId);
        assertThat(after.set().gradedCount()).isEqualTo(1);
        assertThat(after.items().getFirst().latestAttempt().score()).isEqualTo(68);
        assertThat(translation.history(PageRequest.of(0, 10)).getContent()).extracting(r -> r.id())
                .containsExactly(submitted.id());

        // A job that gives up after the grade landed must not turn it into a failure.
        writer.markFailed(submitted.id(), "AI_TIMEOUT");
        assertThat(translation.attempt(submitted.id()).status()).isEqualTo(TranslationAttemptStatus.GRADED);
    }

    @Test
    void anAttemptIsReadableOnlyByItsAuthor() {
        publish();
        SignedIn.as(jdbc, learner);
        UUID itemId = translation.setDetail(setId).items().getFirst().id();
        UUID attemptId = translation.submit(new SubmitTranslationCommand(itemId, "I live here.")).id();

        SignedIn.as(jdbc, other);
        assertThatThrownBy(() -> translation.attempt(attemptId)).isInstanceOf(NotFoundException.class);
        assertThat(translation.history(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void aFailedGradeIsReported() {
        publish();
        SignedIn.as(jdbc, learner);
        UUID itemId = translation.setDetail(setId).items().getFirst().id();
        UUID attemptId = translation.submit(new SubmitTranslationCommand(itemId, "I live here.")).id();

        writer.markFailed(attemptId, "TRANSLATION_REPORT_INVALID");

        var failed = translation.attempt(attemptId);
        assertThat(failed.status()).isEqualTo(TranslationAttemptStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("TRANSLATION_REPORT_INVALID");
        assertThat(failed.referenceEn()).isNotNull();
    }

    private void publish() {
        SignedIn.as(jdbc, staff);
        authoring.publish(setId);
    }

    private UUID firstItemAsLearner() {
        UUID itemId = jdbc
                .sql("select id from translation_items where translation_set_id = :s order by order_no limit 1")
                .param("s", setId).query(UUID.class).single();
        SignedIn.as(jdbc, learner);
        return itemId;
    }
}
