package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.englow3.assessment.api.AssessmentBandReader;
import com.englow3.exam.api.FullTestCompleted;
import com.englow3.exam.entity.AttemptSectionResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.FullTestSitting;
import com.englow3.exam.entity.FullTestSittingStatus;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.repository.AttemptSectionResultRepository;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.FullTestRepository;
import com.englow3.exam.repository.FullTestSittingRepository;
import com.englow3.exam.service.impl.FullTestSittingServiceImpl;
import com.englow3.shared.error.ConflictException;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.PlacementRecorder;
import com.englow3.user.api.UserDirectory;

class FullTestSittingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);

    private final FullTestRepository fullTestRepo = mock(FullTestRepository.class);
    private final FullTestSittingRepository sittingRepo = mock(FullTestSittingRepository.class);
    private final ExamAttemptRepository attemptRepo = mock(ExamAttemptRepository.class);
    private final AttemptSectionResultRepository sectionResultRepo = mock(AttemptSectionResultRepository.class);
    private final AssessmentBandReader assessments = mock(AssessmentBandReader.class);
    private final LearnerGoalReader goals = mock(LearnerGoalReader.class);
    private final PlacementRecorder placements = mock(PlacementRecorder.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final FullTestSittingService service = new FullTestSittingServiceImpl(fullTestRepo, sittingRepo,
            attemptRepo, sectionResultRepo, assessments, goals, placements, userDirectory, events, CLOCK,
            mock(FullTestService.class));

    private final UUID userId = UUID.randomUUID();
    private FullTest test;

    @BeforeEach
    void anIeltsLearnerAndAPublishedFullTest() {
        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(sittingRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        test = FullTest.draft("IELTS Academic 1", "", CertificateVariant.ACADEMIC, UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID());
        test.submitForReview(
                new FullTest.PaperFacts(com.englow3.exam.entity.ExamStatus.PUBLISHED, ExamType.MOCK,
                        CertificateType.IELTS, CertificateVariant.ACADEMIC, true, true, true),
                facts(), CLOCK.instant());
        test.approve(UUID.randomUUID(),
                new FullTest.PaperFacts(com.englow3.exam.entity.ExamStatus.PUBLISHED, ExamType.MOCK,
                        CertificateType.IELTS, CertificateVariant.ACADEMIC, true, true, true),
                facts(), CLOCK.instant());
        when(fullTestRepo.findById(test.getId())).thenReturn(Optional.of(test));
    }

    private static List<FullTest.TaskFacts> facts() {
        return List.of(
                new FullTest.TaskFacts(FullTest.Slot.WRITING_TASK_1, true, "WRITING", "TASK_1", "IELTS", true, true),
                new FullTest.TaskFacts(FullTest.Slot.WRITING_TASK_2, true, "WRITING", "TASK_2", "IELTS", true),
                new FullTest.TaskFacts(FullTest.Slot.SPEAKING_PART_1, true, "SPEAKING", "PART_1", "IELTS", true),
                new FullTest.TaskFacts(FullTest.Slot.SPEAKING_PART_2, true, "SPEAKING", "PART_2", "IELTS", true),
                new FullTest.TaskFacts(FullTest.Slot.SPEAKING_PART_3, true, "SPEAKING", "PART_3", "IELTS", true));
    }

    private void aims(String certificate, boolean certificateLearner) {
        when(goals.read(userId)).thenReturn(Optional.of(new LearnerGoalReader.Goal(userId, null, certificateLearner,
                certificate, null, null, null, null, null, List.of(), List.of())));
    }

    @Test
    void refusesALearnerWhoIsNotPreparingForIelts() {
        aims("TOEIC", true);

        assertThatThrownBy(() -> service.start(test.getId())).isInstanceOf(ConflictException.class)
                .extracting(error -> ((ConflictException) error).getCode()).isEqualTo("FULL_TEST_IELTS_ONLY");
        verify(sittingRepo, never()).save(any());
    }

    @Test
    void handsBackTheSittingThatIsStillOpen() {
        aims("IELTS", true);
        FullTestSitting open = FullTestSitting.start(test.getId(), userId, CLOCK.instant().minusSeconds(3600));
        when(sittingRepo.findFirstByUserIdAndFullTestIdAndStatus(userId, test.getId(),
                FullTestSittingStatus.IN_PROGRESS)).thenReturn(Optional.of(open));
        when(sittingRepo.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));

        assertThat(service.start(test.getId()).id()).isEqualTo(open.getId());
        verify(sittingRepo, never()).save(any());
    }

    @Test
    void recordsTheOverallBandAsThePlacementAndAnnouncesIt() {
        FullTestSitting sitting = FullTestSitting.start(test.getId(), userId, CLOCK.instant().minusSeconds(3600));
        when(sittingRepo.findByIdForUpdate(sitting.getId())).thenReturn(Optional.of(sitting));
        Exam exam = Exam.draft("IELTS L+R", "", ExamType.MOCK, CertificateType.IELTS, CertificateVariant.ACADEMIC,
                TargetLevel.B2, 3600, new BigDecimal("80"), null, UUID.randomUUID());
        ExamAttempt paper = ExamAttempt.start(exam, userId, 80, CLOCK.instant().minusSeconds(1800));
        paper.score(new BigDecimal("60"), 60, CLOCK.instant().minusSeconds(60));
        when(attemptRepo.findFirstByUserIdAndExamIdAndModeAndStatusAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
                userId, test.getExamId(), ExamAttemptMode.FULL, ExamAttemptStatus.SCORED, sitting.getStartedAt()))
                        .thenReturn(Optional.of(paper));
        when(sectionResultRepo.findByExamAttemptId(paper.getId())).thenReturn(List.of(
                AttemptSectionResult.of(paper.getId(), SectionType.LISTENING, BigDecimal.TEN, BigDecimal.TEN,
                        new BigDecimal("7.0"), 30, 40),
                AttemptSectionResult.of(paper.getId(), SectionType.READING, BigDecimal.TEN, BigDecimal.TEN,
                        new BigDecimal("7.0"), 30, 40)));
        for (UUID taskId : test.taskIds()) {
            when(assessments.latestAnswer(taskId, userId, sitting.getStartedAt())).thenReturn(Optional
                    .of(new AssessmentBandReader.Answer(UUID.randomUUID(), taskId, CLOCK.instant().minusSeconds(30),
                            true, new BigDecimal("6.0"), "COMPLETED", CLOCK.instant().minusSeconds(10))));
        }

        var result = service.view(sitting.getId());

        assertThat(result.status()).isEqualTo(FullTestSittingStatus.COMPLETED);
        verify(placements).recordIeltsBand(userId, paper.getId(), "B2", new BigDecimal("6.5"));
        verify(events).publishEvent(any(FullTestCompleted.class));
    }

    @Test
    void recordsTheProvisionalBandWhileWritingAndSpeakingAreStillOut() {
        FullTestSitting sitting = FullTestSitting.start(test.getId(), userId, CLOCK.instant().minusSeconds(3600));
        when(sittingRepo.findByIdForUpdate(sitting.getId())).thenReturn(Optional.of(sitting));
        Exam exam = Exam.draft("IELTS L+R", "", ExamType.MOCK, CertificateType.IELTS, CertificateVariant.ACADEMIC,
                TargetLevel.B2, 3600, new BigDecimal("80"), null, UUID.randomUUID());
        ExamAttempt paper = ExamAttempt.start(exam, userId, 80, CLOCK.instant().minusSeconds(1800));
        paper.score(new BigDecimal("60"), 60, CLOCK.instant().minusSeconds(60));
        when(attemptRepo.findFirstByUserIdAndExamIdAndModeAndStatusAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
                any(), any(), any(), any(), any())).thenReturn(Optional.of(paper));
        when(sectionResultRepo.findByExamAttemptId(paper.getId())).thenReturn(List.of(
                AttemptSectionResult.of(paper.getId(), SectionType.LISTENING, BigDecimal.TEN, BigDecimal.TEN,
                        new BigDecimal("7.5"), 33, 40),
                AttemptSectionResult.of(paper.getId(), SectionType.READING, BigDecimal.TEN, BigDecimal.TEN,
                        new BigDecimal("6.0"), 25, 40)));

        service.refresh(sitting.getId());

        verify(placements).recordIeltsBand(eq(userId), eq(paper.getId()), eq("B2"), eq(new BigDecimal("6.0")));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void saysWhichPartTheGraderGaveUpOn() {
        FullTestSitting sitting = FullTestSitting.start(test.getId(), userId, CLOCK.instant().minusSeconds(3600));
        when(sittingRepo.findByIdForUpdate(sitting.getId())).thenReturn(Optional.of(sitting));
        UUID task2 = test.taskId(FullTest.Slot.WRITING_TASK_2);
        UUID failed = UUID.randomUUID();
        var answer = new AssessmentBandReader.Answer(failed, task2, CLOCK.instant().minusSeconds(30), false, null,
                "FAILED", null);
        when(assessments.latestAnswer(task2, userId, sitting.getStartedAt())).thenReturn(Optional.of(answer));
        when(assessments.read(failed, userId)).thenReturn(Optional.of(answer));

        var parts = service.view(sitting.getId()).parts();

        assertThat(parts.get(FullTest.Slot.WRITING_TASK_2.ordinal()).grading())
                .isEqualTo(com.englow3.exam.entity.FullTestPartGrading.FAILED);
        assertThat(parts.get(FullTest.Slot.WRITING_TASK_1.ordinal()).grading()).isNull();
    }
}
