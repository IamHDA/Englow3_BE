package com.englow3.dictation.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** One submission. Retyping a sentence appends a row rather than replacing one - that history is the point. */
@Entity
@Table(name = "dictation_attempts")
@Getter
public class DictationAttempt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "dictation_lesson_id", nullable = false, updatable = false)
    private UUID dictationLessonId;

    @Column(name = "dictation_sentence_id", nullable = false, updatable = false)
    private UUID dictationSentenceId;

    @Column(nullable = false)
    private String response;

    @Column(name = "accuracy_percent", nullable = false)
    private BigDecimal accuracyPercent;

    @Column(name = "correct_word_count", nullable = false)
    private int correctWordCount;

    @Column(name = "total_word_count", nullable = false)
    private int totalWordCount;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected DictationAttempt() {
    }

    public static DictationAttempt of(UUID userId, UUID lessonId, UUID sentenceId, String response,
            BigDecimal accuracyPercent, int correctWordCount, int totalWordCount, Instant attemptedAt) {
        DictationAttempt attempt = new DictationAttempt();
        attempt.id = UUID.randomUUID();
        attempt.userId = userId;
        attempt.dictationLessonId = lessonId;
        attempt.dictationSentenceId = sentenceId;
        attempt.response = response == null ? "" : response;
        attempt.accuracyPercent = accuracyPercent;
        attempt.correctWordCount = correctWordCount;
        attempt.totalWordCount = totalWordCount;
        attempt.attemptedAt = attemptedAt;
        return attempt;
    }
}
