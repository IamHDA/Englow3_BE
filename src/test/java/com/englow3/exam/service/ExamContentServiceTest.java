package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.projection.AdminExamPaperProjection;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamMediaResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetRepository;
import com.englow3.exam.repository.SectionPartRepository;
import com.englow3.exam.service.impl.ExamContentServiceImpl;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.PresignedUrlResolver;

class ExamContentServiceTest {

    private static final String EXAM_BUCKET = "exams";

    private final ExamRepository examRepo = mock(ExamRepository.class);
    private final ExamSectionRepository examSectionRepo = mock(ExamSectionRepository.class);
    private final SectionPartRepository sectionPartRepo = mock(SectionPartRepository.class);
    private final QuestionSetRepository questionSetRepo = mock(QuestionSetRepository.class);
    private final QuestionRepository questionRepo = mock(QuestionRepository.class);
    private final QuestionOptionRepository questionOptionRepo = mock(QuestionOptionRepository.class);
    private final com.englow3.exam.repository.QuestionAcceptedAnswerRepository acceptedAnswerRepo = mock(
            com.englow3.exam.repository.QuestionAcceptedAnswerRepository.class);
    private final AdminExamPaperQuery examPaperQuery = mock(AdminExamPaperQuery.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final ExamContentService service = new ExamContentServiceImpl(examRepo, examSectionRepo, sectionPartRepo,
            questionSetRepo, questionRepo, questionOptionRepo, acceptedAnswerRepo, examPaperQuery, objectStorage,
            mock(PresignedUrlResolver.class), EXAM_BUCKET, Duration.ofHours(1));

    @Test
    void uploadsMediaThroughTheObjectStorageClient() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        UUID examId = exam.getId();
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp3", "audio/mpeg", "ID3audio-bytes".getBytes());

        ExamMediaResult result = service.uploadMedia(examId, file);

        assertThat(result.objectKey()).startsWith(examId + "/audios/").endsWith(".mp3");
        verify(objectStorage).upload(eq(EXAM_BUCKET), eq(result.objectKey()), any(), eq(file.getSize()),
                eq("audio/mpeg"));
    }

    @Test
    void refusesMediaWhoseBytesAreNotTheDeclaredFormat() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "not-a-picture".getBytes());

        assertThatThrownBy(() -> service.uploadMedia(exam.getId(), file)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("MEDIA_CONTENT_MISMATCH");
        verifyNoInteractions(objectStorage);
    }

    /** A file for a paper that is not there, or can no longer change, would be an orphan in the bucket. */
    @Test
    void uploadsNothingForAMissingOrPublishedPaper() {
        UUID missing = UUID.randomUUID();
        when(examRepo.findById(missing)).thenReturn(Optional.empty());
        Exam archived = AdminExamServiceTest.draft();
        archived.archive();
        when(examRepo.findById(archived.getId())).thenReturn(Optional.of(archived));
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp3", "audio/mpeg", "audio-bytes".getBytes());

        assertThatThrownBy(() -> service.uploadMedia(missing, file)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.uploadMedia(archived.getId(), file)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void replacesContentDeletesBottomUpBeforeReloadingTheTree() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examPaperQuery.loadForAdmin(exam.getId()))
                .thenReturn(Optional.of(new AdminExamPaperProjection(exam, List.of())));

        ExamDetailResult result = service.replaceContent(new UpdateExamContentCommand(exam.getId(), List.of()));

        InOrder deletionOrder = inOrder(acceptedAnswerRepo, questionOptionRepo, questionRepo, questionSetRepo,
                sectionPartRepo, examSectionRepo);
        deletionOrder.verify(acceptedAnswerRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(questionOptionRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(questionRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(questionSetRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(sectionPartRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(examSectionRepo).deleteAllForExam(exam.getId());
        assertThat(result.id()).isEqualTo(exam.getId());
    }

    @Test
    void refusesWritingAndSpeakingSectionsBecauseAMockExamOnlyScoresChoices() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        var writing = new UpdateExamContentCommand.SectionCommand(SectionType.WRITING, 1,
                new java.math.BigDecimal("10"), false, null, List.of());

        assertThatThrownBy(() -> service.replaceContent(new UpdateExamContentCommand(exam.getId(), List.of(writing))))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("EXAM_PRODUCTIVE_SECTION");
    }

    @Test
    void failsToReplaceContentOnAPaperThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(examRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceContent(new UpdateExamContentCommand(missing, List.of())))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("EXAM_NOT_FOUND");
    }

    private static UpdateExamContentCommand withOneQuestion(UUID examId,
            UpdateExamContentCommand.QuestionCommand question) {
        var set = new UpdateExamContentCommand.QuestionSetCommand(null, null, 1, null, null, null, null,
                List.of(question));
        var part = new UpdateExamContentCommand.PartCommand(1, "Part 1", null, null, null, null, List.of(set));
        var section = new UpdateExamContentCommand.SectionCommand(SectionType.LISTENING, 1, java.math.BigDecimal.ONE,
                false, null, List.of(part));
        return new UpdateExamContentCommand(examId, List.of(section));
    }

    private static UpdateExamContentCommand.QuestionCommand question(com.englow3.exam.entity.QuestionType type,
            List<UpdateExamContentCommand.OptionCommand> options,
            List<UpdateExamContentCommand.AcceptedAnswerCommand> accepted) {
        return new UpdateExamContentCommand.QuestionCommand(type, "Name: Sarah {{1}}",
                com.englow3.exam.entity.DifficultyLevel.MEDIUM, com.englow3.exam.entity.SkillType.LISTENING, null, 1,
                java.math.BigDecimal.ONE, null, null, options, accepted);
    }

    @SuppressWarnings("unchecked")
    @Test
    void savesTheAcceptedAnswersOfATextQuestion() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examPaperQuery.loadForAdmin(exam.getId()))
                .thenReturn(Optional.of(new AdminExamPaperProjection(exam, List.of())));

        service.replaceContent(withOneQuestion(exam.getId(), question(com.englow3.exam.entity.QuestionType.TEXT_ENTRY,
                List.of(), List.of(new UpdateExamContentCommand.AcceptedAnswerCommand(" Mitchell ", false, 1)))));

        var saved = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(acceptedAnswerRepo).saveAll(saved.capture());
        assertThat((Iterable<com.englow3.exam.entity.QuestionAcceptedAnswer>) saved.getValue()).singleElement()
                .satisfies(answer -> assertThat(answer.getAnswerText()).isEqualTo("Mitchell"));
    }

    @Test
    void refusesOptionsOnATextQuestion() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.replaceContent(withOneQuestion(exam.getId(),
                question(com.englow3.exam.entity.QuestionType.TEXT_ENTRY,
                        List.of(new UpdateExamContentCommand.OptionCommand("A", 1, true, null)), List.of()))))
                                .isInstanceOf(BadRequestException.class)
                                .extracting(e -> ((BadRequestException) e).getCode())
                                .isEqualTo("TEXT_QUESTION_HAS_OPTIONS");
    }

    @Test
    void refusesAcceptedAnswersOnAChoiceQuestion() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.replaceContent(withOneQuestion(exam.getId(),
                question(com.englow3.exam.entity.QuestionType.SINGLE_CHOICE, List.of(),
                        List.of(new UpdateExamContentCommand.AcceptedAnswerCommand("museum", false, 1))))))
                                .isInstanceOf(BadRequestException.class)
                                .extracting(e -> ((BadRequestException) e).getCode())
                                .isEqualTo("CHOICE_QUESTION_HAS_ACCEPTED_ANSWERS");
    }
}
