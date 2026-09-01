package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class ExamTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

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

    private static Exam toeicDraft(CertificateVariant variant) {
        return draft(CertificateType.TOEIC, variant);
    }

    private static Exam draft(CertificateType type, CertificateVariant variant) {
        return Exam.draft("TOEIC Practice Test 1", "Two skills, seven parts", ExamType.MOCK, type, variant,
                TargetLevel.B1, 7200, new BigDecimal("200.00"), new BigDecimal("600.0"), ADMIN_ID);
    }
}
