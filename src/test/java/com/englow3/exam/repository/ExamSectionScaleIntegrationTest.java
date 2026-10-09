package com.englow3.exam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/** The query behind the "questions must add up to their section" rule, against real tables. */
class ExamSectionScaleIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ExamRepository exams;

    @Autowired
    private JdbcClient jdbc;

    /** Two questions worth 1 point each under a section that declares 100: the fixture's default, and off its scale. */
    @Test
    void namesTheSectionWhoseQuestionsDoNotCarryItsPoints() {
        ExamFixture fixture = new ExamFixture(jdbc);
        UUID author = new LearnerFixture(jdbc).learner();
        UUID exam = fixture.publishedExam("Off scale", author);
        UUID section = fixture.section(exam, 1, "READING");
        UUID set = fixture.questionSet(fixture.part(section, 1, "Part 5"), 1);
        fixture.question(set, 1, "First");
        fixture.question(set, 2, "Second");

        assertThat(exams.findSectionOrderNosOffTheirScale(exam)).containsExactly(1);

        jdbc.sql("update exam_sections set max_raw_score = 2 where id = :id").param("id", section).update();

        assertThat(exams.findSectionOrderNosOffTheirScale(exam)).isEmpty();
    }
}
