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

    @Test
    void ordersByScoreHighestFirstThenTheSeriesOrder() {
        // score = 100 + (n % 3) * 50: n % 3 == 2 -> 200, == 1 -> 150, == 0 -> 100
        assertThat(numbers(page(ExamCatalogueSort.SCORE_DESC, 0, 20))).containsExactly(2, 5, 8, 11, 1, 4, 7, 10, 3, 6,
                9, 12);
    }
}
