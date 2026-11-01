package com.englow3.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.assessment.api.SpeakingLevelReader;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class SpeakingLevelReaderIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SpeakingLevelReader reader;

    @Test
    void turnsAGradedAnswerIntoTheBandTheSeededTableGives() {
        UUID learner = new LearnerFixture(jdbc).learner();

        assertThat(levelFor(learner, "6.0")).isEqualTo("B2");
        assertThat(levelFor(learner, "7.0")).isEqualTo("C1");
        assertThat(levelFor(learner, "4.5")).isEqualTo("B1");
        assertThat(levelFor(learner, "3.5")).isEqualTo("A2");
        assertThat(levelFor(learner, "2.0")).isEqualTo("A1");
    }

    @Test
    void saysAnUngradedAnswerIsNotDoneYet() {
        UUID learner = new LearnerFixture(jdbc).learner();
        UUID attempt = speakingAttempt(learner, "QUEUED", null);

        var level = reader.read(attempt, learner).orElseThrow();

        assertThat(level.completed()).isFalse();
        assertThat(level.level()).isNull();
        assertThat(level.taskId()).isNotNull();
    }

    @Test
    void showsNobodyElsesAnswer() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID owner = fixture.learner();
        UUID attempt = speakingAttempt(owner, "COMPLETED", "{\"overall\": 6.0}");

        assertThat(reader.read(attempt, fixture.learner())).isEmpty();
    }

    private String levelFor(UUID learner, String overall) {
        UUID attempt = speakingAttempt(learner, "COMPLETED", "{\"overall\": " + overall + "}");
        return reader.read(attempt, learner).orElseThrow().level();
    }

    private UUID speakingAttempt(UUID learner, String status, String report) {
        UUID task = UUID.randomUUID();
        jdbc.sql("""
                insert into assessment_tasks (id, skill, title, task_type, instructions, rubric_notes, sample_answer,
                                              minimum_words, time_limit_seconds, status, created_by_user_id, purpose)
                values (:id, 'SPEAKING', 'Level check', 'PART_2', 'Talk', '', '', 0, 120, 'PUBLISHED', :author,
                        'LEVEL_CHECK')
                """).param("id", task).param("author", learner).update();
        UUID attempt = UUID.randomUUID();
        jdbc.sql("""
                insert into assessment_attempts (id, user_id, task_id, client_key, skill, task_snapshot,
                                                 audio_object_key, status, report, submitted_at, assessed_at)
                values (:id, :user, :task, :key, 'SPEAKING', '{}'::jsonb, 'speaking/x.wav', :status,
                        cast(:report as jsonb), now(), case when :status = 'COMPLETED' then now() end)
                """).param("id", attempt).param("user", learner).param("task", task).param("key", UUID.randomUUID())
                .param("status", status).param("report", report).update();
        return attempt;
    }
}
