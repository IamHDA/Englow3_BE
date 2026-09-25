package com.englow3.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.learning.dto.result.ContentReviewResult;
import com.englow3.learning.repository.DictationLessonRepository;
import com.englow3.learning.repository.FlashcardSetRepository;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * Content read back from the database before anyone has reviewed it.
 * <p>
 * All four review columns of a fresh draft are null, and Hibernate loads an embeddable whose columns are all null as
 * {@code null} - overriding the entity's own {@code new ReviewTrail()}. So every draft that had been written and then
 * read again had no trail: the admin content list failed with a NullPointerException, and so did submitting the draft
 * for review, which left no way to publish it. Unit tests never saw it because they build the entity in memory, where
 * the initializer holds.
 */
class ReviewTrailIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private FlashcardSetRepository sets;

    @Autowired
    private DictationLessonRepository lessons;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void listsADraftThatNobodyHasReviewed() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID setId = fixture.draftFlashcardSet("Never reviewed", fixture.learner());

        transactions.executeWithoutResult(status -> {
            var set = sets.findById(setId).orElseThrow();

            assertThat(set.getReview()).isNotNull();
            assertThatCode(() -> ContentReviewResult.of(set, 0)).doesNotThrowAnyException();
        });
    }

    @Test
    void submitsADraftReadBackFromTheDatabase() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID setId = fixture.draftFlashcardSet("Ready for review", fixture.learner());
        fixture.flashcard(setId, 1, "acquire");

        transactions
                .executeWithoutResult(status -> sets.findById(setId).orElseThrow().submitForReview(1, Instant.now()));

        transactions.executeWithoutResult(
                status -> assertThat(sets.findById(setId).orElseThrow().getReview().getSubmittedForReviewAt())
                        .isNotNull());
    }

    @Test
    void readsALessonThatNobodyHasReviewed() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID lessonId = fixture.publishedDictationLesson("Never reviewed", fixture.learner());

        transactions.executeWithoutResult(
                status -> assertThat(lessons.findById(lessonId).orElseThrow().getReview()).isNotNull());
    }
}
