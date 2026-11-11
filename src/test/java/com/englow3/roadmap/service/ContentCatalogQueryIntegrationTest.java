package com.englow3.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.helper.RoadmapPlan.Candidate;
import com.englow3.roadmap.query.ContentCatalogQuery;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class ContentCatalogQueryIntegrationTest extends PostgresIntegrationTest {
    @Test
    @org.springframework.transaction.annotation.Transactional
    void refusesScoreMilestonesWhenToeicReferenceDataIsMissing() {
        jdbc.sql("delete from score_conversions where certificate_type='TOEIC'").update();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalog.mockPapers("TOEIC"))
                .isInstanceOf(com.englow3.shared.error.ConflictException.class).hasMessageContaining("conversion");
    }

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ContentCatalogQuery catalog;

    /** An IELTS learner gets general practice and IELTS practice, never TOEIC practice. */
    @Test
    void offersGeneralContentAndTheLearnersOwnCertificateOnly() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID author = fixture.learner();
        UUID general = level(fixture.publishedQuiz("General " + author, author), "quizzes", null);
        UUID ielts = level(fixture.publishedQuiz("IELTS " + author, author), "quizzes", "IELTS");
        UUID toeic = level(fixture.publishedQuiz("TOEIC " + author, author), "quizzes", "TOEIC");
        UUID toeicLesson = level(fixture.publishedDictationLesson("TOEIC lesson " + author, author),
                "dictation_lessons", "TOEIC");
        UUID ieltsLesson = level(fixture.publishedDictationLesson("IELTS lesson " + author, author),
                "dictation_lessons", "IELTS");

        var offered = catalog.candidates(CefrLevel.C2, CefrLevel.C2, "IELTS", 500).stream().map(Candidate::id).toList();

        assertThat(offered).contains(general, ielts, ieltsLesson).doesNotContain(toeic, toeicLesson);
    }

    /** At C2 so other tests' content at lower levels does not crowd these out of the per-kind limit. */
    private UUID level(UUID id, String table, String certificate) {
        jdbc.sql("update " + table + " set target_level = 'C2', certificate_type = :certificate where id = :id")
                .param("certificate", certificate).param("id", id).update();
        return id;
    }
}
