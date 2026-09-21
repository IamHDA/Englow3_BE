package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.query.ExamGradingQuery;
import com.englow3.exam.query.ExamGradingQuery.GradingOption;
import com.englow3.exam.query.ExamGradingQuery.GradingQuestion;
import com.englow3.exam.query.LearnerExamPaperQuery;
import com.englow3.exam.repository.AttemptAnswerOptionRepository;
import com.englow3.exam.repository.AttemptAnswerRepository;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

class LearnerExamServiceTest {

    private final ExamRepository examRepo = mock(ExamRepository.class);
    private final ExamAttemptRepository attemptRepo = mock(ExamAttemptRepository.class);
    private final AttemptAnswerRepository answerRepo = mock(AttemptAnswerRepository.class);
    private final AttemptAnswerOptionRepository answerOptionRepo = mock(AttemptAnswerOptionRepository.class);
    private final LearnerExamPaperQuery paperQuery = mock(LearnerExamPaperQuery.class);
    private final ExamGradingQuery gradingQuery = mock(ExamGradingQuery.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final LearnerExamService service = new LearnerExamService(examRepo, attemptRepo, answerRepo,
            answerOptionRepo, paperQuery, gradingQuery, userDirectory);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void identifyCaller() {
        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(attemptRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startsANewAttemptFromThePublishedPaperSnapshot() {
        Exam exam = publishedExam();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(attemptRepo.findFirstByUserIdAndExamIdAndStatusOrderByStartedAtDesc(userId, exam.getId(),
                ExamAttemptStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(examRepo.countQuestions(exam.getId())).thenReturn(2L);

        ExamAttemptResult result = service.start(exam.getId());

        assertThat(result.status()).isEqualTo(ExamAttemptStatus.IN_PROGRESS);
        assertThat(result.questionCount()).isEqualTo(2);
        assertThat(result.resumed()).isFalse();
        assertThat(result.expiresAt()).isAfter(result.startedAt());
    }

    @Test
    void returnsTheExistingUnexpiredAttemptInsteadOfCreatingAnother() {
        Exam exam = publishedExam();
        ExamAttempt active = ExamAttempt.start(exam, userId, 2, Instant.now());
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(attemptRepo.findFirstByUserIdAndExamIdAndStatusOrderByStartedAtDesc(userId, exam.getId(),
                ExamAttemptStatus.IN_PROGRESS)).thenReturn(Optional.of(active));

        ExamAttemptResult result = service.start(exam.getId());

        assertThat(result.id()).isEqualTo(active.getId());
        assertThat(result.resumed()).isTrue();
        verify(attemptRepo, never()).save(any());
    }

    @Test
    void gradesAndPersistsAnExactSingleChoiceAnswer() {
        Exam exam = publishedExam();
        ExamAttempt attempt = ExamAttempt.start(exam, userId, 2, Instant.now());
        UUID questionId = UUID.randomUUID();
        UUID correctOptionId = UUID.randomUUID();
        UUID wrongOptionId = UUID.randomUUID();
        GradingQuestion question = new GradingQuestion(questionId, QuestionType.SINGLE_CHOICE, BigDecimal.ONE,
                "Because it fits the sentence", List.of(new GradingOption(correctOptionId, true, "Correct form"),
                        new GradingOption(wrongOptionId, false, "Wrong tense")));
        when(attemptRepo.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(gradingQuery.load(exam.getId())).thenReturn(List.of(question));

        ExamAttemptResult result = service.submit(new SubmitExamAttemptCommand(attempt.getId(),
                List.of(new SubmittedAnswer(questionId, List.of(correctOptionId)))));

        assertThat(result.status()).isEqualTo(ExamAttemptStatus.SCORED);
        assertThat(result.rawScore()).isEqualByComparingTo("1");
        assertThat(result.correctAnswerCount()).isEqualTo(1);
        assertThat(result.questions()).singleElement().satisfies(review -> {
            assertThat(review.correct()).isTrue();
            assertThat(review.correctOptionIds()).containsExactly(correctOptionId);
            assertThat(review.options()).extracting(option -> option.explanation()).containsExactly("Correct form",
                    "Wrong tense");
        });
        verify(answerRepo).saveAll(any());
        verify(answerOptionRepo).saveAll(any());
    }

    @Test
    void rejectsAnOptionThatDoesNotBelongToTheQuestion() {
        Exam exam = publishedExam();
        ExamAttempt attempt = ExamAttempt.start(exam, userId, 1, Instant.now());
        UUID questionId = UUID.randomUUID();
        GradingQuestion question = new GradingQuestion(questionId, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, null,
                List.of(new GradingOption(UUID.randomUUID(), true, null)));
        when(attemptRepo.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(gradingQuery.load(exam.getId())).thenReturn(List.of(question));

        assertThatThrownBy(() -> service.submit(new SubmitExamAttemptCommand(attempt.getId(),
                List.of(new SubmittedAnswer(questionId, List.of(UUID.randomUUID()))))))
                        .isInstanceOf(BadRequestException.class)
                        .extracting(error -> ((BadRequestException) error).getCode())
                        .isEqualTo("OPTION_NOT_IN_QUESTION");

        verify(answerRepo, never()).saveAll(any());
    }

    @Test
    void hidesAnAttemptOwnedByAnotherUser() {
        Exam exam = publishedExam();
        ExamAttempt someoneElsesAttempt = ExamAttempt.start(exam, UUID.randomUUID(), 1, Instant.now());
        when(attemptRepo.findById(someoneElsesAttempt.getId())).thenReturn(Optional.of(someoneElsesAttempt));

        assertThatThrownBy(() -> service.result(someoneElsesAttempt.getId())).isInstanceOf(NotFoundException.class)
                .extracting(error -> ((NotFoundException) error).getCode()).isEqualTo("EXAM_ATTEMPT_NOT_FOUND");
    }

    private static Exam publishedExam() {
        Exam exam = Exam.draft("TOEIC Practice Test", "Listening and reading", ExamType.MOCK, CertificateType.TOEIC,
                CertificateVariant.LR, TargetLevel.B1, 7200, new BigDecimal("2.00"), null, UUID.randomUUID());
        exam.publish(1, 2, new BigDecimal("2.00"), Instant.now());
        return exam;
    }
}
