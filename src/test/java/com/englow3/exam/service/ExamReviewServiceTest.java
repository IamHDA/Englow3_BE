package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

class ExamReviewServiceTest {

    private final ExamRepository examRepo = mock(ExamRepository.class);
    private final QuestionRepository questionRepo = mock(QuestionRepository.class);
    private final ExamReviewService service = new com.englow3.exam.service.impl.ExamReviewServiceImpl(examRepo,
            questionRepo, mock(UserDirectory.class));

    @BeforeEach
    void completeQuestions() {
        when(questionRepo.findIncompleteQuestionOrderNos(any())).thenReturn(List.of());
    }

    @Test
    void publishesWithTheFiguresItReadFromTheRepository() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepo.countSections(exam.getId())).thenReturn(2L);
        when(examRepo.countQuestions(exam.getId())).thenReturn(200L);
        when(examRepo.sumSectionScores(exam.getId())).thenReturn(new BigDecimal("200.00"));

        ExamResult result = service.publish(new PublishExamCommand(exam.getId()));

        assertThat(result.status()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void archivesThePaperItWasGiven() {
        Exam exam = AdminExamServiceTest.draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThat(service.archive(new ArchiveExamCommand(exam.getId())).status()).isEqualTo(ExamStatus.ARCHIVED);
    }

    @Test
    void failsToPublishAPaperThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(examRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(new PublishExamCommand(missing))).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("EXAM_NOT_FOUND");
    }
}
