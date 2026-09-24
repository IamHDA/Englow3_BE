package com.englow3.speaking.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A sentence a learner is asked to say, and everything shown alongside it.
 * <p>
 * The review trail is inline rather than borrowed from {@code learning.ReviewTrail}: persistence types do not cross
 * module boundaries, so the four columns are repeated here for the same reason the exam module repeats them.
 */
@Entity
@Table(name = "speaking_prompts")
@Getter
public class SpeakingPrompt {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(name = "target_level")
    private String targetLevel;

    /**
     * What the learner is asked to say. The assessment scores their recording against this, so a prompt without one
     * would produce a number measuring nothing.
     */
    @Column(name = "reference_text", nullable = false)
    private String referenceText;

    @Column(name = "ipa_transcript")
    private String ipaTranscript;

    @Column(name = "translation_vi")
    private String translationVi;

    @Column(name = "phoneme_target")
    private String phonemeTarget;

    /** A JSON array of coaching notes, stored and returned as text - nothing here reads inside it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String tips;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpeakingPromptStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected SpeakingPrompt() {
    }

    public static SpeakingPrompt draft(String slug, String title, String category, String targetLevel,
            String referenceText, String ipaTranscript, String translationVi, String phonemeTarget, String tipsJson,
            UUID createdByUserId) {
        requireReferenceText(referenceText);

        SpeakingPrompt prompt = new SpeakingPrompt();
        prompt.id = UUID.randomUUID();
        prompt.slug = slug;
        prompt.title = title;
        prompt.category = category;
        prompt.targetLevel = targetLevel;
        prompt.referenceText = referenceText.strip();
        prompt.ipaTranscript = ipaTranscript;
        prompt.translationVi = translationVi;
        prompt.phonemeTarget = phonemeTarget;
        prompt.tips = tipsJson == null ? "[]" : tipsJson;
        prompt.status = SpeakingPromptStatus.DRAFT;
        prompt.createdByUserId = createdByUserId;
        return prompt;
    }

    /** Publishing straight from a draft, for an administrator who holds the approval power anyway. */
    public void publish(Instant now) {
        if (status != SpeakingPromptStatus.DRAFT) {
            throw new ConflictException("SPEAKING_PROMPT_NOT_DRAFT",
                    "Only a draft prompt can be published; this one is %s".formatted(status));
        }
        this.status = SpeakingPromptStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void submitForReview(Instant now) {
        if (status != SpeakingPromptStatus.DRAFT && status != SpeakingPromptStatus.REJECTED) {
            throw new ConflictException("SPEAKING_PROMPT_NOT_SUBMITTABLE",
                    "Only a draft or rejected prompt can be submitted; this one is %s".formatted(status));
        }
        this.status = SpeakingPromptStatus.PENDING_REVIEW;
        this.submittedForReviewAt = now;
    }

    public void approve(UUID reviewerId, Instant now) {
        requirePendingReview("approved");
        this.status = SpeakingPromptStatus.PUBLISHED;
        this.publishedAt = now;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = null;
    }

    public void reject(UUID reviewerId, String note, Instant now) {
        requirePendingReview("rejected");
        if (note == null || note.isBlank()) {
            throw new BadRequestException("REVIEW_NOTE_REQUIRED", "A rejection must say why");
        }
        this.status = SpeakingPromptStatus.REJECTED;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = note.strip();
    }

    public void archive() {
        if (status == SpeakingPromptStatus.ARCHIVED) {
            throw new ConflictException("SPEAKING_PROMPT_ALREADY_ARCHIVED", "This prompt is already archived");
        }
        this.status = SpeakingPromptStatus.ARCHIVED;
    }

    private void requirePendingReview(String verb) {
        if (status != SpeakingPromptStatus.PENDING_REVIEW) {
            throw new ConflictException("SPEAKING_PROMPT_NOT_PENDING_REVIEW",
                    "Only a prompt waiting on review can be %s; this one is %s".formatted(verb, status));
        }
    }

    private static void requireReferenceText(String referenceText) {
        if (referenceText == null || referenceText.isBlank()) {
            throw new BadRequestException("SPEAKING_PROMPT_NO_REFERENCE_TEXT",
                    "A prompt needs the sentence the learner is asked to say");
        }
    }
}
