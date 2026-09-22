package com.englow3.speaking.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * How one word of one recording was scored.
 * <p>
 * Plain CRUD - written once when the assessment lands and never changed, so there is no invariant to guard and no
 * behaviour to put here.
 */
@Entity
@Table(name = "speaking_attempt_words")
@Getter
public class SpeakingAttemptWord {

    @Id
    private UUID id;

    @Column(name = "speaking_attempt_id", nullable = false, updatable = false)
    private UUID speakingAttemptId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String word;

    @Column(name = "accuracy_percent")
    private BigDecimal accuracyPercent;

    /** The provider's own label - Mispronunciation, Omission, Insertion, None. Stored as it came. */
    @Column(name = "error_type")
    private String errorType;

    @Column(name = "offset_ms")
    private Integer offsetMs;

    @Column(name = "duration_ms")
    private Integer durationMs;

    /** A JSON array of per-phoneme scores, read as a unit with the word. */
    @Column(columnDefinition = "jsonb", nullable = false)
    private String phonemes;

    protected SpeakingAttemptWord() {
    }

    public static SpeakingAttemptWord of(UUID speakingAttemptId, int orderNo, String word, BigDecimal accuracyPercent,
            String errorType, Integer offsetMs, Integer durationMs, String phonemesJson) {
        SpeakingAttemptWord row = new SpeakingAttemptWord();
        row.id = UUID.randomUUID();
        row.speakingAttemptId = speakingAttemptId;
        row.orderNo = orderNo;
        row.word = word;
        row.accuracyPercent = accuracyPercent;
        row.errorType = errorType;
        row.offsetMs = offsetMs;
        row.durationMs = durationMs;
        row.phonemes = phonemesJson == null ? "[]" : phonemesJson;
        return row;
    }
}
