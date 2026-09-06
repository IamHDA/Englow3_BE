package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamMediaResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.dto.result.QuestionImportResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetRepository;
import com.englow3.exam.repository.SectionPartRepository;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.spreadsheet.SpreadsheetReader;
import com.englow3.shared.spreadsheet.SpreadsheetTable;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.service.UserDirectory;

class AdminExamServiceTest {
    private static final String EXAM_BUCKET = "exams";

    private final ExamRepository examRepo = mock(ExamRepository.class);

    private final ExamSectionRepository examSectionRepo = mock(ExamSectionRepository.class);

    private final SectionPartRepository sectionPartRepo = mock(SectionPartRepository.class);

    private final QuestionSetRepository questionSetRepo = mock(QuestionSetRepository.class);

    private final QuestionRepository questionRepo = mock(QuestionRepository.class);

    private final QuestionOptionRepository questionOptionRepo = mock(QuestionOptionRepository.class);

    private final AdminExamPaperQuery examPaperQuery = mock(AdminExamPaperQuery.class);

    private final UserDirectory userDirectory = mock(UserDirectory.class);

    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);

    private final SpreadsheetReader spreadsheetReader = mock(SpreadsheetReader.class);

    private final AdminExamService service = new AdminExamService(examRepo, examSectionRepo, sectionPartRepo,
            questionSetRepo, questionRepo, questionOptionRepo, examPaperQuery, userDirectory, objectStorage,
            EXAM_BUCKET, spreadsheetReader);

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void passTheGate() {
        when(userDirectory.requireCurrentUserId()).thenReturn(adminId);
        when(examRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionRepo.findIncompleteQuestionOrderNos(any())).thenReturn(List.of());
    }

    private static Exam draft() {
        CreateExamCommand command = command();
        return Exam.draft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore(), UUID.randomUUID());
    }

    private static Exam published() {
        Exam exam = draft();
        exam.publish(1, 1, exam.getMaxRawScore(), List.of(), Instant.now());
        return exam;
    }

    private static CreateExamCommand command() {
        return new CreateExamCommand("TOEIC Practice Test 1", "Two skills, seven parts", ExamType.MOCK,
                CertificateType.TOEIC, CertificateVariant.LR, TargetLevel.B1, 7200, new BigDecimal("200.00"),
                new BigDecimal("600.0"));
    }

    @Nested
    class Success {

        @Test
        void stampsTheDraftWithTheAdminsOwnUserId() {
            ExamResult result = service.create(command());

            ArgumentCaptor<Exam> saved = ArgumentCaptor.forClass(Exam.class);
            verify(examRepo).save(saved.capture());
            assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(adminId);
            assertThat(result.id()).isEqualTo(saved.getValue().getId());
        }

        @Test
        void handsBackThePaperTheQueryLoaded() {
            Exam exam = draft();
            ExamDetailResult loaded = ExamDetailResult.of(exam, List.of());
            when(examPaperQuery.loadForAdmin(exam.getId())).thenReturn(Optional.of(loaded));

            assertThat(service.detail(new ExamDetailCommand(exam.getId()))).isSameAs(loaded);
        }

        @Test
        void editsThePaperItWasGiven() {
            Exam exam = draft();
            when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

            ExamResult result = service.update(new UpdateExamCommand(exam.getId(), "TOEIC Practice Test 2", "Revised",
                    ExamType.MOCK, CertificateType.TOEIC, CertificateVariant.LR, TargetLevel.B2, 7200,
                    new BigDecimal("200.00"), new BigDecimal("650.0")));

            assertThat(result.title()).isEqualTo("TOEIC Practice Test 2");
            assertThat(result.targetLevel()).isEqualTo(TargetLevel.B2);
            assertThat(result.status()).isEqualTo(ExamStatus.DRAFT);
        }

        @Test
        void publishesWithTheFiguresItReadFromTheRepository() {
            Exam exam = draft();
            when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
            when(examRepo.countSections(exam.getId())).thenReturn(2L);
            when(examRepo.countQuestions(exam.getId())).thenReturn(200L);
            when(examRepo.sumSectionScores(exam.getId())).thenReturn(new BigDecimal("200.00"));

            ExamResult result = service.publish(new PublishExamCommand(exam.getId()));

            assertThat(result.status()).isEqualTo(ExamStatus.PUBLISHED);
        }

        @Test
        void archivesThePaperItWasGiven() {
            Exam exam = draft();
            when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

            assertThat(service.archive(new ArchiveExamCommand(exam.getId())).status()).isEqualTo(ExamStatus.ARCHIVED);
        }

        @Test
        void uploadsMediaThroughTheObjectStorageClient() {
            UUID examId = UUID.randomUUID();
            MockMultipartFile file = new MockMultipartFile("file", "clip.mp3", "audio/mpeg", "audio-bytes".getBytes());

            ExamMediaResult result = service.uploadMedia(examId, file);

            assertThat(result.objectKey()).startsWith(examId + "/audios/").endsWith(".mp3");
            verify(objectStorage).upload(eq(EXAM_BUCKET), eq(result.objectKey()), any(), eq(file.getSize()),
                    eq("audio/mpeg"));
        }

        @Test
        void importsQuestionsOnADraftPaperByReadingTheFileThenApplyingTheColumnContract() {
            Exam exam = draft();
            when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
            MultipartFile file = new MockMultipartFile("file", "questions.csv", "text/csv", "irrelevant".getBytes());
            SpreadsheetTable table = SpreadsheetTable.of(List.of(
                    List.of("Nội dung", "Dạng câu", "Phương án", "Đáp án đúng", "Giải thích"),
                    List.of("Question?", "single", "OptA;OptB", "A", "")));
            when(spreadsheetReader.read(file)).thenReturn(table);

            QuestionImportResult result = service.importQuestions(exam.getId(), file);

            assertThat(result.errors()).isEmpty();
            assertThat(result.questions()).extracting(QuestionImportResult.ImportedQuestion::content)
                    .containsExactly("Question?");
        }

        @Test
        void replacesContentDeletesBottomUpBeforeReloadingTheTree() {
            Exam exam = draft();
            when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
            ExamDetailResult reloaded = ExamDetailResult.of(exam, List.of());
            when(examPaperQuery.loadForAdmin(exam.getId())).thenReturn(Optional.of(reloaded));
            UpdateExamContentCommand command = new UpdateExamContentCommand(exam.getId(), List.of());

            ExamDetailResult result = service.replaceContent(command);

            // Every FK below `exams` is `on delete restrict` (V008-V013) - a parent deleted before its children fails.
            InOrder deletionOrder = inOrder(questionOptionRepo, questionRepo, questionSetRepo, sectionPartRepo,
                    examSectionRepo);
            deletionOrder.verify(questionOptionRepo).deleteAllForExam(exam.getId());
            deletionOrder.verify(questionRepo).deleteAllForExam(exam.getId());
            deletionOrder.verify(questionSetRepo).deleteAllForExam(exam.getId());
            deletionOrder.verify(sectionPartRepo).deleteAllForExam(exam.getId());
            deletionOrder.verify(examSectionRepo).deleteAllForExam(exam.getId());
            assertThat(result).isSameAs(reloaded);
        }

    }

    @Nested
    class Failure {

        @Test
        void failsToLoadAPaperThatDoesNotExist() {
            UUID missing = UUID.randomUUID();
            when(examPaperQuery.loadForAdmin(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.detail(new ExamDetailCommand(missing)))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("EXAM_NOT_FOUND");
        }

        @Test
        void failsToEditAPaperThatDoesNotExist() {
            UUID missing = UUID.randomUUID();
            when(examRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(new UpdateExamCommand(missing, "x", "y", ExamType.MOCK, null, null,
                    null, 60, BigDecimal.ONE, null))).isInstanceOf(NotFoundException.class)
                            .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("EXAM_NOT_FOUND");
        }

        @Test
        void failsToReplaceContentOnAPaperThatDoesNotExist() {
            UUID missing = UUID.randomUUID();
            when(examRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replaceContent(new UpdateExamContentCommand(missing, List.of())))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("EXAM_NOT_FOUND");
        }

        @Test
        void failsToPublishAPaperThatDoesNotExist() {
            UUID missing = UUID.randomUUID();
            when(examRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publish(new PublishExamCommand(missing)))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("EXAM_NOT_FOUND");
        }

        @Test
        void failsToImportQuestionsIntoAPaperThatDoesNotExist() {
            UUID missing = UUID.randomUUID();
            when(examRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.importQuestions(missing, mock(MultipartFile.class)))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("EXAM_NOT_FOUND");
        }

        @Test
        void refusesToImportQuestionsIntoAPublishedPaper() {
            Exam exam = published();
            when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> service.importQuestions(exam.getId(), mock(MultipartFile.class)))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("EXAM_NOT_DRAFT");
        }

    }

}
