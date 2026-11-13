package com.englow3.dictation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.dictation.repository.DictationLessonRepository;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class ReviewTrailIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private DictationLessonRepository lessons;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void readsALessonThatNobodyHasReviewed() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID lessonId = fixture.publishedDictationLesson("Never reviewed", fixture.learner());

        transactions.executeWithoutResult(
                status -> assertThat(lessons.findById(lessonId).orElseThrow().getReview()).isNotNull());
    }
}
