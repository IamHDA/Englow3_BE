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
        Exam exam = toeicDraft(CertificateVariant.LR);

        assertThat(exam.getId()).isNotNull();
        assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
        assertThat(exam.getVersionNumber()).isEqualTo(1);
        assertThat(exam.getPublishedAt()).isNull();
        assertThat(exam.getCreatedByUserId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void draftsAPaperThatIsTiedToNoCertificate() {
        Exam exam = draft(null, null);

        assertThat(exam.getCertificateType()).isNull();
        assertThat(exam.getCertificateVariant()).isNull();
    }

    @Test
    void refusesAVariantThatBelongsToAnotherCertificate() {
        assertThatThrownBy(() -> toeicDraft(CertificateVariant.ACADEMIC)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_CERTIFICATE_VARIANT_MISMATCH");
    }

    @Test
    void refusesACertificatePaperThatNamesNoVariant() {
        assertThatThrownBy(() -> toeicDraft(null)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_CERTIFICATE_VARIANT_REQUIRED");
    }

    @Test
    void refusesAVariantOnAPaperThatNamesNoCertificate() {
        assertThatThrownBy(() -> draft(null, CertificateVariant.LR)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_VARIANT_WITHOUT_CERTIFICATE");
    }

    @Test
    void publishesAPaperWhoseSectionsAddUpToItsDeclaredScore() {
        Exam exam = toeicDraft(CertificateVariant.LR);
        Instant now = Instant.parse("2026-09-01T10:00:00Z");

        exam.publish(2, 200, DECLARED_SCORE, now);

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(exam.getPublishedAt()).isEqualTo(now);
    }

    /** numeric(8,2) summed is not guaranteed to keep the scale the paper declared, and BigDecimal.equals cares. */
    @Test
    void acceptsTheSameTotalWrittenWithADifferentScale() {
        Exam exam = toeicDraft(CertificateVariant.LR);

        exam.publish(2, 200, new BigDecimal("200.0"), Instant.now());

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void refusesToPublishAPaperWithNoSection() {
        assertThatThrownBy(() -> toeicDraft(CertificateVariant.LR).publish(0, 0, BigDecimal.ZERO, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_HAS_NO_SECTION");
    }

    @Test
    void refusesToPublishAPaperWithNoQuestion() {
        assertThatThrownBy(() -> toeicDraft(CertificateVariant.LR).publish(2, 0, DECLARED_SCORE, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_HAS_NO_QUESTION");
    }

    @Test
    void refusesToPublishWhenTheSectionsDoNotAddUpToTheDeclaredScore() {
        assertThatThrownBy(
                () -> toeicDraft(CertificateVariant.LR).publish(2, 200, new BigDecimal("195.00"), Instant.now()))
                        .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                        .isEqualTo("EXAM_SCORE_MISMATCH");
    }

    @Test
    void refusesToPublishAPaperTwice() {
        Exam exam = published();

        assertThatThrownBy(() -> exam.publish(2, 200, DECLARED_SCORE, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_NOT_DRAFT");
    }

    @Test
    void archivesADraftAndAPublishedPaperAlike() {
        Exam draft = toeicDraft(CertificateVariant.LR);
        Exam published = published();

        draft.archive();
        published.archive();

        assertThat(draft.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
        assertThat(published.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
    }

    @Test
    void refusesToArchiveTwice() {
        Exam exam = toeicDraft(CertificateVariant.LR);
        exam.archive();

        assertThatThrownBy(exam::archive).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("EXAM_ALREADY_ARCHIVED");
    }

    @Test
    void refusesToPublishAnArchivedPaper() {
        Exam exam = toeicDraft(CertificateVariant.LR);
        exam.archive();

        assertThatThrownBy(() -> exam.publish(2, 200, DECLARED_SCORE, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("EXAM_NOT_DRAFT");
    }

    private static Exam published() {
        Exam exam = toeicDraft(CertificateVariant.LR);
        exam.publish(2, 200, DECLARED_SCORE, Instant.now());
        return exam;
    }

    private static Exam toeicDraft(CertificateVariant variant) {
        return draft(CertificateType.TOEIC, variant);
    }

    private static Exam draft(CertificateType type, CertificateVariant variant) {
        return Exam.draft("TOEIC Practice Test 1", "Two skills, seven parts", ExamType.MOCK, type, variant,
                TargetLevel.B1, 7200, DECLARED_SCORE, new BigDecimal("600.0"), ADMIN_ID);
    }
}
