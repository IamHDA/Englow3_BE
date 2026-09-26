package com.englow3.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.flashcard.dto.result.ContentReviewResult;
import com.englow3.flashcard.repository.FlashcardSetRepository;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class ReviewTrailIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private FlashcardSetRepository sets;

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
}
