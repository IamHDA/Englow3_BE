package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetRepository;
import com.englow3.exam.repository.SectionPartRepository;
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
    private final AdminExamPaperQuery examPaperQuery = mock(AdminExamPaperQuery.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final ExamContentService service = new com.englow3.exam.service.impl.ExamContentServiceImpl(examRepo,
            examSectionRepo, sectionPartRepo, questionSetRepo, questionRepo, questionOptionRepo, examPaperQuery,
            objectStorage, mock(PresignedUrlResolver.class), EXAM_BUCKET, Duration.ofHours(1));

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
    void replacesContentDeletesBottomUpBeforeReloadingTheTree() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examPaperQuery.loadForAdmin(exam.getId()))
                .thenReturn(Optional.of(new AdminExamPaperProjection(exam, List.of())));

        ExamDetailResult result = service.replaceContent(new UpdateExamContentCommand(exam.getId(), List.of()));

        InOrder deletionOrder = inOrder(questionOptionRepo, questionRepo, questionSetRepo, sectionPartRepo,
                examSectionRepo);
        deletionOrder.verify(questionOptionRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(questionRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(questionSetRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(sectionPartRepo).deleteAllForExam(exam.getId());
        deletionOrder.verify(examSectionRepo).deleteAllForExam(exam.getId());
        assertThat(result.id()).isEqualTo(exam.getId());
    }

    @Test
    void failsToReplaceContentOnAPaperThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(examRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceContent(new UpdateExamContentCommand(missing, List.of())))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("EXAM_NOT_FOUND");
    }
}
