package com.englow3.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.shared.persistence.SqlTime;

/**
 * Rows for a read model to read.
 * <p>
 * Written as SQL rather than through the entities on purpose. These tests exist to check hand-written queries against a
 * real database; building the fixture through the same JPA mappings the queries deliberately bypass would let a
 * mismatch between the two hide, which is the one thing this is here to catch.
 * <p>
 * Every learner gets their own id, so tests sharing a container cannot see each other's rows and none of them needs to
 * clean up after itself.
 */
public final class LearnerFixture {

    private final JdbcClient jdbc;

    public LearnerFixture(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A learner and nothing else. Every count for them starts at zero, which is itself worth testing. */
    public UUID learner() {
        UUID id = UUID.randomUUID();
        String email = "learner-%s@example.com".formatted(id);
        jdbc.sql("""
                insert into users (id, auth_provider_id, email, role, full_name, display_name, onboarding_step)
                values (:id, :authId, :email, 'LEARNER', 'Test Learner', 'Test', 'COMPLETED')
                """).param("id", id).param("authId", UUID.randomUUID()).param("email", email).update();

        return id;
    }

    public UUID publishedFlashcardSet(String name, UUID authorId) {
        return flashcardSet(name, authorId, "PUBLISHED");
    }

    public UUID draftFlashcardSet(String name, UUID authorId) {
        return flashcardSet(name, authorId, "DRAFT");
    }

    private UUID flashcardSet(String name, UUID authorId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into flashcard_sets (id, slug, name, description, topic, status, created_by_user_id)
                values (:id, :slug, :name, '', 'general', :status, :authorId)
                """).param("id", id).param("slug", "set-" + id).param("name", name).param("status", status)
                .param("authorId", authorId).update();

        return id;
    }

    public UUID flashcard(UUID setId, int orderNo, String lemma) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into flashcards (id, flashcard_set_id, order_no, lemma, part_of_speech, sense_label,
                                        ipa_us, definition_en, definition_vi, example_sentence)
                values (:id, :setId, :orderNo, :lemma, 'noun', :lemma, '/test/', 'A definition.',
                        'Mot dinh nghia.', 'An example.')
                """).param("id", id).param("setId", setId).param("orderNo", orderNo).param("lemma", lemma).update();

        return id;
    }

    /** A card on the learner's schedule. {@code dueAt} in the past is what makes it due. */
    public void review(UUID userId, UUID cardId, String status, Instant dueAt) {
        jdbc.sql("""
                insert into flashcard_reviews (id, user_id, flashcard_id, status, repetitions, ease_factor,
                                               interval_days, due_at, lapse_count)
                values (:id, :userId, :cardId, :status, 1, 2.50, 1, :dueAt, 0)
                """).param("id", UUID.randomUUID()).param("userId", userId).param("cardId", cardId)
                .param("status", status).param("dueAt", SqlTime.at(dueAt)).update();
    }

    /** One answer, as the log records it. The log is what the streak and the experience counter read. */
    public void reviewLog(UUID userId, UUID cardId, UUID setId, String rating, Instant reviewedAt) {
        jdbc.sql("""
                insert into flashcard_review_logs (id, user_id, flashcard_id, flashcard_set_id, rating,
                                                   time_spent_seconds, reviewed_at)
                values (:id, :userId, :cardId, :setId, :rating, 5, :reviewedAt)
                """).param("id", UUID.randomUUID()).param("userId", userId).param("cardId", cardId)
                .param("setId", setId).param("rating", rating).param("reviewedAt", SqlTime.at(reviewedAt)).update();
    }

    public UUID publishedQuiz(String title, UUID authorId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into quizzes (id, slug, title, description, category, time_limit_seconds,
                                     passing_score_percent, status, created_by_user_id)
                values (:id, :slug, :title, '', 'grammar', 600, 70, 'PUBLISHED', :authorId)
                """).param("id", id).param("slug", "quiz-" + id).param("title", title).param("authorId", authorId)
                .update();

        return id;
    }

    /** A null {@code submittedAt} is an attempt still open - started, never handed in. */
    public void quizAttempt(UUID userId, UUID quizId, Integer scorePercent, boolean passed, Instant submittedAt) {
        Instant startedAt = submittedAt == null ? Instant.now() : submittedAt;
        jdbc.sql("""
                insert into quiz_attempts (id, quiz_id, user_id, status, started_at, expires_at, score, max_score,
                                           score_percentage, correct_answer_count, question_count, passed,
                                           submitted_at)
                values (:id, :quizId, :userId, :status, :startedAt, :expiresAt, :scorePercent, 100,
                        :scorePercent, 8, 10, :passed, :submittedAt)
                """).param("id", UUID.randomUUID()).param("quizId", quizId).param("userId", userId)
                .param("status", submittedAt == null ? "IN_PROGRESS" : "SCORED")
                .param("startedAt", SqlTime.at(startedAt)).param("expiresAt", SqlTime.at(startedAt.plusSeconds(600)))
                .param("scorePercent", scorePercent).param("passed", passed)
                .param("submittedAt", SqlTime.at(submittedAt)).update();
    }

    public UUID publishedDictationLesson(String title, UUID authorId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into dictation_lessons (id, slug, title, topic, status, created_by_user_id)
                values (:id, :slug, :title, 'travel', 'PUBLISHED', :authorId)
                """).param("id", id).param("slug", "lesson-" + id).param("title", title).param("authorId", authorId)
                .update();

        return id;
    }

    public UUID dictationSentence(UUID lessonId, int orderNo, String text) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into dictation_sentences (id, dictation_lesson_id, order_no, text, audio_object_key,
                                                 audio_duration_seconds, hint_word_count)
                values (:id, :lessonId, :orderNo, :text, :key, 4, 6)
                """).param("id", id).param("lessonId", lessonId).param("orderNo", orderNo).param("text", text)
                .param("key", "dictation/%s.mp3".formatted(id)).update();

        return id;
    }

    public void dictationAttempt(UUID userId, UUID lessonId, UUID sentenceId, String response, BigDecimal accuracy,
            Instant attemptedAt) {
        jdbc.sql("""
                insert into dictation_attempts (id, user_id, dictation_lesson_id, dictation_sentence_id, response,
                                                accuracy_percent, correct_word_count, total_word_count, attempted_at)
                values (:id, :userId, :lessonId, :sentenceId, :response, :accuracy, 5, 6, :attemptedAt)
                """).param("id", UUID.randomUUID()).param("userId", userId).param("lessonId", lessonId)
                .param("sentenceId", sentenceId).param("response", response).param("accuracy", accuracy)
                .param("attemptedAt", SqlTime.at(attemptedAt)).update();
    }
}
