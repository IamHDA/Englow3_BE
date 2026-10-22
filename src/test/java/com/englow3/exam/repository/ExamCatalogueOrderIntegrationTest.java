package com.englow3.exam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.helper.ExamCatalogueSort;
import com.englow3.shared.page.StablePage;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * The order the exam catalogue comes back in, against the real database. A batch of numbered papers is created in one
 * instant, so everything ties on the creation time - the case that made the list arbitrary and its pages repeat and
 * skip rows.
 */
class ExamCatalogueOrderIntegrationTest extends PostgresIntegrationTest {

    private static final String SERIES = "Order Series Test ";

    @Autowired
    private ExamRepository exams;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void createNumberedSeries() {
        ExamFixture fixture = new ExamFixture(jdbc);
        UUID author = new LearnerFixture(jdbc).learner();
        // Created out of order on purpose, so neither insertion order nor id order can pass for the right answer.
        for (int number : new int[] { 10, 3, 12, 1, 7, 2, 11, 5, 9, 4, 8, 6 }) {
            UUID id = fixture.publishedExam(SERIES + number, author);
            String level = number <= 4 ? "A1" : number <= 8 ? "B1" : "C1";
            jdbc.sql("update exams set target_level = :level, max_raw_score = :score, "
                    + "created_at = timestamp '2026-01-01 00:00:00' where id = :id").param("level", level)
                    .param("score", 100 + (number % 3) * 50).param("id", id).update();
        }
    }

    /** The database outlives each test, so a series left behind would be counted again by the next one. */
    @AfterEach
    void removeTheSeries() {
        jdbc.sql("delete from exam_attempts where exam_id in (select id from exams where title like :prefix)")
                .param("prefix", SERIES + "%").update();
        jdbc.sql("delete from exams where title like :prefix").param("prefix", SERIES + "%").update();
    }

    private Page<Exam> page(ExamCatalogueSort sort, int page, int size) {
        Pageable pageable = StablePage.of(PageRequest.of(page, size, sort.toSort()));
        return exams.searchCatalogue(ExamStatus.PUBLISHED, ExamType.MOCK, null, null, null, SERIES.trim(), pageable);
    }

    private static List<Integer> numbers(Page<Exam> page) {
        return page.getContent().stream().map(e -> Integer.parseInt(e.getTitle().substring(SERIES.length()))).toList();
    }

    /** "Test 2" before "Test 10": plain text order would put 10, 11 and 12 straight after 1. */
    @Test
    void countsANumberedSeriesInOrderEvenWhenEveryRowTiesOnCreationTime() {
        assertThat(numbers(page(ExamCatalogueSort.NEWEST, 0, 20))).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                12);
    }

    /** Page two must not repeat a row from page one or skip one. */
    @Test
    void pagesWithoutRepeatingOrSkipping() {
        List<Integer> seen = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            seen.addAll(numbers(page(ExamCatalogueSort.NEWEST, p, 5)));
        }

        assertThat(seen).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    /** Sorting by level orders the whole catalogue, not the eight papers that happen to be on screen. */
    @Test
    void ordersTheWholeCatalogueByLevelAcrossPages() {
        List<Integer> ascending = new ArrayList<>();
        List<Integer> descending = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            ascending.addAll(numbers(page(ExamCatalogueSort.LEVEL_ASC, p, 5)));
            descending.addAll(numbers(page(ExamCatalogueSort.LEVEL_DESC, p, 5)));
        }

        assertThat(ascending).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(descending).containsExactly(9, 10, 11, 12, 5, 6, 7, 8, 1, 2, 3, 4);
    }

    private UUID idOf(int number) {
        return jdbc.sql("select id from exams where title = :title").param("title", SERIES + number).query(UUID.class)
                .single();
    }

    private void sat(UUID learner, int number, String mode, double percentage) {
        jdbc.sql("""
                insert into exam_attempts (id, exam_id, exam_version_number, user_id, status, started_at, expires_at,
                                           max_raw_score, question_count, score_percentage, mode)
                values (:id, :exam, 1, :user, 'SCORED', now(), now() + interval '1 hour', 100, 10, :pct, :mode)
                """).param("id", UUID.randomUUID()).param("exam", idOf(number)).param("user", learner)
                .param("pct", percentage).param("mode", mode).update();
    }

    private List<Integer> bestScoreOrder(UUID learner, int page, int size) {
        return numbers(exams.searchCatalogueByBestScore(ExamStatus.PUBLISHED, ExamType.MOCK, null, null, null,
                SERIES.trim(), learner, PageRequest.of(page, size)));
    }

    /**
     * "Highest score" is the learner's own best on each paper, not the maximum the paper declares - which put a paper
     * they had scored 40% on above one they had scored 90% on. Papers they have not sat follow, in series order, and a
     * practice is not a score on the paper.
     */
    @Test
    void ordersByTheLearnersOwnBestScoreWithUnsatPapersLast() {
        UUID learner = new LearnerFixture(jdbc).learner();
        sat(learner, 3, "FULL", 40);
        sat(learner, 3, "FULL", 55);
        sat(learner, 7, "FULL", 90);
        sat(learner, 10, "FULL", 70);
        sat(learner, 1, "PRACTICE", 99);

        assertThat(bestScoreOrder(learner, 0, 20)).containsExactly(7, 10, 3, 1, 2, 4, 5, 6, 8, 9, 11, 12);
    }

    @Test
    void anotherLearnersScoresDoNotMoveThePapers() {
        UUID someoneElse = new LearnerFixture(jdbc).learner();
        UUID learner = new LearnerFixture(jdbc).learner();
        sat(someoneElse, 12, "FULL", 100);

        assertThat(bestScoreOrder(learner, 0, 20)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void pagesByBestScoreWithoutRepeatingOrSkipping() {
        UUID learner = new LearnerFixture(jdbc).learner();
        sat(learner, 8, "FULL", 80);
        sat(learner, 2, "FULL", 60);

        List<Integer> seen = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            seen.addAll(bestScoreOrder(learner, p, 5));
        }

        assertThat(seen).containsExactly(8, 2, 1, 3, 4, 5, 6, 7, 9, 10, 11, 12);
    }
}
