package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class ExamTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    private static final BigDecimal DECLARED_SCORE = new BigDecimal("200.00");

    @Test
    void draftsAShellThatIsNotYetPublished() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);

        assertThat(exam.getId()).isNotNull();
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
        assertThat(exam.getVersionNumber()).isEqualTo(1);
        assertThat(exam.getPublishedAt()).isNull();
        assertThat(exam.getCreatedByUserId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void draftsAPaperThatIsTiedToNoCertificate() {
        Exam exam = buildDraft(null, null);

        assertThat(exam.getCertificateType()).isNull();
        assertThat(exam.getCertificateVariant()).isNull();
    }

    @Test
    void refusesAVariantThatBelongsToAnotherCertificate() {
        assertThatThrownBy(() -> buildToeicDraft(CertificateVariant.ACADEMIC)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_CERTIFICATE_VARIANT_MISMATCH");
    }

    @Test
    void refusesACertificatePaperThatNamesNoVariant() {
        assertThatThrownBy(() -> buildToeicDraft(null)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_CERTIFICATE_VARIANT_REQUIRED");
    }

    @Test
    void refusesAVariantOnAPaperThatNamesNoCertificate() {
        assertThatThrownBy(() -> buildDraft(null, CertificateVariant.LR)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_VARIANT_WITHOUT_CERTIFICATE");
    }

    @Test
    void editsADraftInPlaceAndLeavesItADraft() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);

        exam.updateDraft("TOEIC Practice Test 2", "Revised", ExamType.PLACEMENT, CertificateType.TOEIC,
                CertificateVariant.SW, TargetLevel.B2, 3600, new BigDecimal("400.00"), null);

        assertThat(exam.getTitle()).isEqualTo("TOEIC Practice Test 2");
        assertThat(exam.getCertificateVariant()).isEqualTo(CertificateVariant.SW);
        assertThat(exam.getPassScore()).isNull();
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
    }

    @Test
    void refusesToEditAPublishedPaper() {
        Exam exam = buildPublishedExam();

        assertThatThrownBy(() -> exam.updateDraft("New title", "d", ExamType.MOCK, CertificateType.TOEIC,
                CertificateVariant.LR, TargetLevel.B1, 7200, DECLARED_SCORE, null))
                        .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                        .isEqualTo("EXAM_NOT_DRAFT");
    }

    /** The same coherence rule as draft(), reached through the other door - an edit must not smuggle IELTS + LR in. */
    @Test
    void refusesAnEditThatMixesACertificateWithAnotherCertificatesVariant() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);

        assertThatThrownBy(() -> exam.updateDraft("t", "d", ExamType.MOCK, CertificateType.TOEIC,
                CertificateVariant.ACADEMIC, TargetLevel.B1, 7200, DECLARED_SCORE, null))
                        .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                        .isEqualTo("EXAM_CERTIFICATE_VARIANT_MISMATCH");
    }

    @Test
    void publishesAPaperWhoseSectionsAddUpToItsDeclaredScore() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);
        Instant now = Instant.parse("2026-09-01T10:00:00Z");

        exam.publish(2, 200, DECLARED_SCORE, now);

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(exam.getPublishedAt()).isEqualTo(now);
    }

    /** numeric(8,2) summed is not guaranteed to keep the scale the paper declared, and BigDecimal.equals cares. */
    @Test
    void acceptsTheSameTotalWrittenWithADifferentScale() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);

        exam.publish(2, 200, new BigDecimal("200.0"), Instant.now());

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void refusesToPublishAPaperWithNoSection() {
        assertThatThrownBy(() -> buildToeicDraft(CertificateVariant.LR).publish(0, 0, BigDecimal.ZERO, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_HAS_NO_SECTION");
    }

    @Test
    void refusesToPublishAPaperWithNoQuestion() {
        assertThatThrownBy(() -> buildToeicDraft(CertificateVariant.LR).publish(2, 0, DECLARED_SCORE, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_HAS_NO_QUESTION");
    }

    @Test
    void refusesToPublishWhenTheSectionsDoNotAddUpToTheDeclaredScore() {
        assertThatThrownBy(
                () -> buildToeicDraft(CertificateVariant.LR).publish(2, 200, new BigDecimal("195.00"), Instant.now()))
                        .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                        .isEqualTo("EXAM_SCORE_MISMATCH");
    }

    @Test
    void refusesToPublishAPaperTwice() {
        Exam exam = buildPublishedExam();

        assertThatThrownBy(() -> exam.publish(2, 200, DECLARED_SCORE, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_NOT_DRAFT");
    }

    @Test
    void archivesADraftAndAPublishedPaperAlike() {
        Exam draft = buildToeicDraft(CertificateVariant.LR);
        Exam published = buildPublishedExam();

        draft.archive();
        published.archive();

        assertThat(draft.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
        assertThat(published.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
    }

    @Test
    void refusesToArchiveTwice() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);
        exam.archive();

        assertThatThrownBy(exam::archive).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("EXAM_ALREADY_ARCHIVED");
    }

    @Test
    void refusesToPublishAnArchivedPaper() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);
        exam.archive();

        assertThatThrownBy(() -> exam.publish(2, 200, DECLARED_SCORE, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_NOT_DRAFT");
    }

    private static Exam buildPublishedExam() {
        Exam exam = buildToeicDraft(CertificateVariant.LR);
        exam.publish(2, 200, DECLARED_SCORE, Instant.now());
        return exam;
    }

    private static Exam buildToeicDraft(CertificateVariant variant) {
        return buildDraft(CertificateType.TOEIC, variant);
    }

    private static Exam buildDraft(CertificateType type, CertificateVariant variant) {
        return Exam.draft("TOEIC Practice Test 1", "Two skills, seven parts", ExamType.MOCK, type, variant,
                TargetLevel.B1, 7200, DECLARED_SCORE, new BigDecimal("600.0"), ADMIN_ID);
    }
}
