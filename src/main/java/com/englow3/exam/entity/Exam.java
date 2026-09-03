package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The one content entity with rules. No {@code @Setter} / {@code @Data}: those would regenerate setters for exactly the
 * fields the rules guard - {@code status}, {@code publishedAt}, {@code versionNumber}.
 */
@Entity
@Table(name = "exams")
@Getter
public class Exam {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false)
    private ExamType examType;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type")
    private CertificateType certificateType;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_variant")
    private CertificateVariant certificateVariant;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level")
    private TargetLevel targetLevel;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    /**
     * Entered rather than derived: no section exists at create time. {@code publish()} is what makes the two agree.
     */
    @Column(name = "max_raw_score", nullable = false)
    private BigDecimal maxRawScore;

    @Column(name = "pass_score")
    private BigDecimal passScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamStatus status;

    /** The paper's own content version, which an edit after publish bumps - not an optimistic lock. */
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Filled by the column default, never by this application - hence not insertable. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Exam() {
    }

    /**
     * A shell only: title, type and scoring, no section. The paper is unusable until content is seeded and
     * {@code publish()} accepts it.
     */
    public static Exam draft(String title, String description, ExamType examType, CertificateType certificateType,
            CertificateVariant certificateVariant, TargetLevel targetLevel, int durationSeconds, BigDecimal maxRawScore,
            BigDecimal passScore, UUID createdByUserId) {
        requireCoherentCertificate(certificateType, certificateVariant);

        Exam exam = new Exam();
        exam.id = UUID.randomUUID();
        exam.title = title;
        exam.description = description;
        exam.examType = examType;
        exam.certificateType = certificateType;
        exam.certificateVariant = certificateVariant;
        exam.targetLevel = targetLevel;
        exam.durationSeconds = durationSeconds;
        exam.maxRawScore = maxRawScore;
        exam.passScore = passScore;
        exam.status = ExamStatus.DRAFT;
        exam.versionNumber = 1;
        exam.createdByUserId = createdByUserId;
        return exam;
    }

    /**
     * The only thing that sets {@code status} to PUBLISHED and stamps {@code publishedAt}. It weighs plain numbers the
     * service counted for it - the entity touches no repository - and refuses a paper that would be unusable once
     * learners can sit it: nothing to sit, or a scoring scale that does not add up.
     */
    public void publish(long sectionCount, long questionCount, BigDecimal sectionsRawTotal, Instant now) {
        if (status != ExamStatus.DRAFT) {
            throw new ConflictException("EXAM_NOT_DRAFT",
                    "Only a draft paper can be published; this one is %s".formatted(status));
        }
        if (sectionCount == 0) {
            throw new ConflictException("EXAM_HAS_NO_SECTION", "A paper with no section cannot be published");
        }
        if (questionCount == 0) {
            throw new ConflictException("EXAM_HAS_NO_QUESTION", "A paper with no question cannot be published");
        }
        // compareTo, not equals: BigDecimal.equals also compares scale, so 200.0 and 200.00 would disagree.
        if (sectionsRawTotal.compareTo(maxRawScore) != 0) {
            throw new ConflictException("EXAM_SCORE_MISMATCH",
                    "Section scores total %s but the paper declares %s".formatted(sectionsRawTotal, maxRawScore));
        }
        this.status = ExamStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /**
     * Retiring a paper, from draft or from publication. There is no delete: every foreign key into this row is
     * {@code on delete restrict}, so a paper anyone has ever sat cannot be removed - and one nobody has sat is still
     * worth keeping for the record.
     */
    public void archive() {
        if (status == ExamStatus.ARCHIVED) {
            throw new ConflictException("EXAM_ALREADY_ARCHIVED", "This paper is already archived");
        }
        this.status = ExamStatus.ARCHIVED;
    }

    /**
     * A variant belongs to exactly one certificate, and the schema says so nowhere - both columns are plain varchar
     * with no check constraint, so this is the only thing standing between the API and an IELTS paper labelled L&R.
     */
    private static void requireCoherentCertificate(CertificateType type, CertificateVariant variant) {
        if (type == null) {
            if (variant != null) {
                throw new BadRequestException("EXAM_VARIANT_WITHOUT_CERTIFICATE",
                        "A paper with no certificate type cannot carry a certificate variant");
            }
            return;
        }
        if (variant == null) {
            throw new BadRequestException("EXAM_CERTIFICATE_VARIANT_REQUIRED",
                    "A %s paper must say which variant it is".formatted(type));
        }
        boolean belongs = switch (type) {
            case TOEIC -> variant == CertificateVariant.LR || variant == CertificateVariant.SW;
            case IELTS -> variant == CertificateVariant.ACADEMIC || variant == CertificateVariant.GENERAL;
        };
        if (!belongs) {
            throw new BadRequestException("EXAM_CERTIFICATE_VARIANT_MISMATCH",
                    "%s has no variant %s".formatted(type, variant));
        }
    }
}
