package com.englow3.translation.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englow3.shared.error.BadRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** One learner's English for one sentence, and what the grader made of it. */
@Entity
@Table(name = "translation_attempts")
@Getter
public class TranslationAttempt {

    /** Long enough for any sentence a set holds, short enough that a pasted essay is refused rather than graded. */
    public static final int MAX_RESPONSE_LENGTH = 1000;

    /**
     * Letters only Vietnamese writes: the toned vowels of Latin Extended Additional and ă â đ ê ô ơ ư. A loanword such
     * as "café" uses none of them, so English that borrows one is still accepted.
     */
    private static final java.util.regex.Pattern VIETNAMESE_LETTER = java.util.regex.Pattern
            .compile("[\\u1EA0-\\u1EF9ăâđêôơưĂÂĐÊÔƠƯ]");

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "translation_set_id", nullable = false, updatable = false)
    private UUID translationSetId;

    @Column(name = "translation_item_id", nullable = false, updatable = false)
    private UUID translationItemId;

    @Column(nullable = false, updatable = false)
    private String response;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranslationAttemptStatus status;

    private Integer score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String feedback;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "graded_at")
    private Instant gradedAt;

    protected TranslationAttempt() {
    }

    public static TranslationAttempt queued(UUID userId, UUID setId, UUID itemId, String response, Instant now) {
        String text = response == null ? "" : response.strip();
        if (text.isEmpty()) {
            throw new BadRequestException("TRANSLATION_RESPONSE_EMPTY", "Write your translation before submitting");
        }
        if (text.length() > MAX_RESPONSE_LENGTH) {
            throw new BadRequestException("TRANSLATION_RESPONSE_TOO_LONG",
                    "A translation can be at most %d characters".formatted(MAX_RESPONSE_LENGTH));
        }
        if (VIETNAMESE_LETTER.matcher(text).find()) {
            // Refused here rather than graded: the model, told to give such an answer nothing for meaning, still
            // credited the meaning of the Vietnamese it was handed - and grading it would spend the learner's
            // allowance.
            throw new BadRequestException("TRANSLATION_NOT_ENGLISH", "Write your translation in English");
        }
        TranslationAttempt attempt = new TranslationAttempt();
        attempt.id = UUID.randomUUID();
        attempt.userId = userId;
        attempt.translationSetId = setId;
        attempt.translationItemId = itemId;
        attempt.response = text;
        attempt.status = TranslationAttemptStatus.QUEUED;
        attempt.createdAt = now;
        return attempt;
    }

    /**
     * Idempotent for a job delivered twice: a graded attempt keeps its first grade, and a failed one is not revived by
     * a late result.
     */
    public void recordGrade(int score, String feedbackJson, Instant now) {
        if (status != TranslationAttemptStatus.QUEUED) {
            return;
        }
        this.score = Math.max(0, Math.min(100, score));
        this.feedback = feedbackJson;
        this.status = TranslationAttemptStatus.GRADED;
        this.gradedAt = now;
    }

    /** Only an attempt still waiting becomes failed; a graded one is not undone by a job that gave up afterwards. */
    public void recordFailure(String errorCode, Instant now) {
        if (status != TranslationAttemptStatus.QUEUED) {
            return;
        }
        this.status = TranslationAttemptStatus.FAILED;
        this.errorCode = errorCode;
        this.gradedAt = now;
    }

    public boolean isGraded() {
        return status == TranslationAttemptStatus.GRADED;
    }
}
