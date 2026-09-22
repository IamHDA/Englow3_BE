package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class ExamTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    private static final BigDecimal DECLARED_SCORE = new BigDecimal("200.00");

    @Nested
    class Success {

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
        void archivesADraftAndAPublishedPaperAlike() {
            Exam draft = buildToeicDraft(CertificateVariant.LR);
            Exam published = buildPublishedExam();

            draft.archive();
            published.archive();

            assertThat(draft.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
            assertThat(published.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
        }
    }

    @Nested
    class Failure {

        @Test
        void refusesAVariantThatBelongsToAnotherCertificate() {
            assertThatThrownBy(() -> buildToeicDraft(CertificateVariant.ACADEMIC))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("EXAM_CERTIFICATE_VARIANT_MISMATCH");
        }

        @Test
        void refusesACertificatePaperThatNamesNoVariant() {
            assertThatThrownBy(() -> buildToeicDraft(null)).isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("EXAM_CERTIFICATE_VARIANT_REQUIRED");
        }

        @Test
        void refusesAVariantOnAPaperThatNamesNoCertificate() {
            assertThatThrownBy(() -> buildDraft(null, CertificateVariant.LR)).isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("EXAM_VARIANT_WITHOUT_CERTIFICATE");
        }

        @Test
        void refusesToEditAPublishedPaper() {
            Exam exam = buildPublishedExam();

            assertThatThrownBy(() -> exam.updateDraft("New title", "d", ExamType.MOCK, CertificateType.TOEIC,
                    CertificateVariant.LR, TargetLevel.B1, 7200, DECLARED_SCORE, null))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("EXAM_NOT_EDITABLE");
        }

        /** The same coherence rule as draft(), through the other door - an edit must not smuggle IELTS + LR in. */
        @Test
        void refusesAnEditThatMixesACertificateWithAnotherCertificatesVariant() {
            Exam exam = buildToeicDraft(CertificateVariant.LR);

            assertThatThrownBy(() -> exam.updateDraft("t", "d", ExamType.MOCK, CertificateType.TOEIC,
                    CertificateVariant.ACADEMIC, TargetLevel.B1, 7200, DECLARED_SCORE, null))
                            .isInstanceOf(BadRequestException.class)
                            .extracting(e -> ((BadRequestException) e).getCode())
                            .isEqualTo("EXAM_CERTIFICATE_VARIANT_MISMATCH");
        }

        @Test
        void refusesToPublishAPaperWithNoSection() {
            assertThatThrownBy(
                    () -> buildToeicDraft(CertificateVariant.LR).publish(0, 0, BigDecimal.ZERO, Instant.now()))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("EXAM_HAS_NO_SECTION");
        }

        @Test
        void refusesToPublishAPaperWithNoQuestion() {
            assertThatThrownBy(
                    () -> buildToeicDraft(CertificateVariant.LR).publish(2, 0, DECLARED_SCORE, Instant.now()))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("EXAM_HAS_NO_QUESTION");
        }

        @Test
        void refusesToPublishWhenTheSectionsDoNotAddUpToTheDeclaredScore() {
            assertThatThrownBy(() -> buildToeicDraft(CertificateVariant.LR).publish(2, 200, new BigDecimal("195.00"),
                    Instant.now())).isInstanceOf(ConflictException.class)
                            .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("EXAM_SCORE_MISMATCH");
        }

        @Test
        void refusesToPublishAPaperTwice() {
            Exam exam = buildPublishedExam();

            assertThatThrownBy(() -> exam.publish(2, 200, DECLARED_SCORE, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("EXAM_NOT_DRAFT");
        }

        @Test
        void refusesToPublishAnArchivedPaper() {
            Exam exam = buildToeicDraft(CertificateVariant.LR);
            exam.archive();

            assertThatThrownBy(() -> exam.publish(2, 200, DECLARED_SCORE, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("EXAM_NOT_DRAFT");
        }

        @Test
        void refusesToArchiveTwice() {
            Exam exam = buildToeicDraft(CertificateVariant.LR);
            exam.archive();

            assertThatThrownBy(exam::archive).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("EXAM_ALREADY_ARCHIVED");
        }
    }

    @Nested
    class Review {

        private static final UUID REVIEWER_ID = UUID.randomUUID();

        @Test
        void sendsADraftToTheQueue() {
            Exam exam = buildToeicDraft(CertificateVariant.LR);
            Instant submittedAt = Instant.now();

            exam.submitForReview(2, 200, DECLARED_SCORE, submittedAt);

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.PENDING_REVIEW);
            assertThat(exam.getSubmittedForReviewAt()).isEqualTo(submittedAt);
            assertThat(exam.getPublishedAt()).isNull();
        }

        /**
         * Checked on the way in, not only on the way out. A reviewer handed a paper with no questions learns nothing
         * they can act on, while the author is the one who can fix it.
         */
        @Test
        void refusesToSubmitAPaperThatCouldNotBePublished() {
            assertThatThrownBy(
                    () -> buildToeicDraft(CertificateVariant.LR).submitForReview(2, 0, DECLARED_SCORE, Instant.now()))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("EXAM_HAS_NO_QUESTION");
        }

        @Test
        void refusesToSubmitAPaperAlreadyWaiting() {
            Exam exam = buildPendingExam();

            assertThatThrownBy(() -> exam.submitForReview(2, 200, DECLARED_SCORE, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("EXAM_NOT_SUBMITTABLE");
        }

        @Test
        void publishesOnApprovalAndRecordsWhoDecided() {
            Exam exam = buildPendingExam();
            Instant approvedAt = Instant.now();

            exam.approve(REVIEWER_ID, 2, 200, DECLARED_SCORE, approvedAt);

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
            assertThat(exam.getPublishedAt()).isEqualTo(approvedAt);
            assertThat(exam.getReviewedByUserId()).isEqualTo(REVIEWER_ID);
            assertThat(exam.getReviewedAt()).isEqualTo(approvedAt);
        }

        /** The paper's sections live in other tables, so approval re-checks rather than trusting submission time. */
        @Test
        void refusesToApproveAPaperThatHasSinceStoppedAddingUp() {
            Exam exam = buildPendingExam();

            assertThatThrownBy(() -> exam.approve(REVIEWER_ID, 2, 200, new BigDecimal("195.00"), Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("EXAM_SCORE_MISMATCH");
        }

        @Test
        void refusesToApproveAPaperNobodySubmitted() {
            Exam exam = buildToeicDraft(CertificateVariant.LR);

            assertThatThrownBy(() -> exam.approve(REVIEWER_ID, 2, 200, DECLARED_SCORE, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("EXAM_NOT_PENDING_REVIEW");
        }

        @Test
        void keepsTheReasonWhenTurningAPaperBack() {
            Exam exam = buildPendingExam();

            exam.reject(REVIEWER_ID, "  Part 3 has no audio.  ", Instant.now());

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.REJECTED);
            assertThat(exam.getReviewNote()).isEqualTo("Part 3 has no audio.");
            assertThat(exam.getReviewedByUserId()).isEqualTo(REVIEWER_ID);
        }

        /** "Rejected" on its own gives the author nothing to change, so the next submission would be a guess. */
        @Test
        void refusesARejectionWithNoReason() {
            Exam exam = buildPendingExam();

            assertThatThrownBy(() -> exam.reject(REVIEWER_ID, "   ", Instant.now()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("EXAM_REVIEW_NOTE_REQUIRED");
        }

        /**
         * The case that decides whether a rejection is a dead end. A paper turned back has to be editable, or its
         * author can never answer the note.
         */
        @Test
        void letsARejectedPaperBeCorrectedAndResubmitted() {
            Exam exam = buildPendingExam();
            exam.reject(REVIEWER_ID, "Part 3 has no audio.", Instant.now());

            exam.updateDraft("TOEIC Practice Test 1b", "Fixed", ExamType.MOCK, CertificateType.TOEIC,
                    CertificateVariant.LR, TargetLevel.B1, 7200, DECLARED_SCORE, null);
            exam.submitForReview(2, 200, DECLARED_SCORE, Instant.now());

            assertThat(exam.getTitle()).isEqualTo("TOEIC Practice Test 1b");
            assertThat(exam.getStatus()).isEqualTo(ExamStatus.PENDING_REVIEW);
        }

        /** Approval clears the old note: it described a paper that no longer exists. */
        @Test
        void dropsAStaleNoteOnApproval() {
            Exam exam = buildPendingExam();
            exam.reject(REVIEWER_ID, "Part 3 has no audio.", Instant.now());
            exam.submitForReview(2, 200, DECLARED_SCORE, Instant.now());

            exam.approve(REVIEWER_ID, 2, 200, DECLARED_SCORE, Instant.now());

            assertThat(exam.getReviewNote()).isNull();
        }

        @Test
        void retiresAPaperWaitingOnReview() {
            Exam exam = buildPendingExam();

            exam.archive();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
        }

        private static Exam buildPendingExam() {
            Exam exam = buildToeicDraft(CertificateVariant.LR);
            exam.submitForReview(2, 200, DECLARED_SCORE, Instant.now());
            return exam;
        }
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
