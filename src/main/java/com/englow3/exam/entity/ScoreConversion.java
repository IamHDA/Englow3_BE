package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One row of a certificate's published raw-score-to-band table. Read-only reference data: rows are added by migration
 * with a later {@code effectiveFrom}, never edited, so a result keeps the band it was given.
 */
@Entity
@Table(name = "score_conversions")
@Getter
public class ScoreConversion {

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false)
    private CertificateType certificateType;

    /** Null when the row holds for every variant (IELTS Listening). */
    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_variant")
    private CertificateVariant certificateVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type")
    private SectionType sectionType;

    @Column(name = "raw_score_from", nullable = false)
    private int rawScoreFrom;

    @Column(name = "raw_score_to", nullable = false)
    private int rawScoreTo;

    @Column(name = "converted_score", nullable = false)
    private BigDecimal convertedScore;

    @Column(name = "cefr_level")
    private String cefrLevel;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    protected ScoreConversion() {
    }

    /** For tests and the conversion helper; the application never writes this table. */
    public static ScoreConversion of(CertificateType certificateType, CertificateVariant certificateVariant,
            SectionType sectionType, int rawScoreFrom, int rawScoreTo, BigDecimal convertedScore,
            LocalDate effectiveFrom) {
        ScoreConversion row = new ScoreConversion();
        row.certificateType = certificateType;
        row.certificateVariant = certificateVariant;
        row.sectionType = sectionType;
        row.rawScoreFrom = rawScoreFrom;
        row.rawScoreTo = rawScoreTo;
        row.convertedScore = convertedScore;
        row.effectiveFrom = effectiveFrom;
        return row;
    }

    public boolean covers(int rawScore) {
        return rawScoreFrom <= rawScore && rawScore <= rawScoreTo;
    }
}
