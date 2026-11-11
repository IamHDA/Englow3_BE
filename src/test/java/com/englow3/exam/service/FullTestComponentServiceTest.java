package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.englow3.exam.entity.*;
import com.englow3.exam.repository.*;
import com.englow3.exam.service.impl.FullTestComponentServiceImpl;
import com.englow3.assessment.api.FullTestAssessmentStarter;
import com.englow3.user.api.UserDirectory;
import com.englow3.shared.error.*;

class FullTestComponentServiceTest {
    private final FullTestSittingRepository sittings = mock(FullTestSittingRepository.class);
    private final FullTestRepository tests = mock(FullTestRepository.class);
    private final FullTestComponentAttemptRepository bindings = mock(FullTestComponentAttemptRepository.class);
    private final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    private final ExamAttemptService exams = mock(ExamAttemptService.class);
    private final FullTestAssessmentStarter assessments = mock(FullTestAssessmentStarter.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    private final UUID user = UUID.randomUUID();
    private final FullTestComponentService service = new FullTestComponentServiceImpl(sittings, tests, bindings,
            attempts, exams, assessments, users, Clock.fixed(now, ZoneOffset.UTC));
    private FullTest test;
    private FullTestSitting sitting;

    @BeforeEach
    void setup() {
        when(users.requireCurrentUserId()).thenReturn(user);
        test = FullTest.draft("IELTS", "", CertificateVariant.ACADEMIC, UUID.randomUUID(),
                java.util.stream.IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID()).toList(), UUID.randomUUID());
        sitting = FullTestSitting.start(test.getId(), user, now.minusSeconds(60));
        when(sittings.findByIdForUpdate(sitting.getId())).thenReturn(Optional.of(sitting));
        when(tests.findById(test.getId())).thenReturn(Optional.of(test));
        when(bindings.save(any())).thenAnswer(c -> c.getArgument(0));
    }

    @Test
    void refusesAnotherLearnersSitting() {
        var foreign = FullTestSitting.start(test.getId(), UUID.randomUUID(), now);
        when(sittings.findByIdForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.startPaper(foreign.getId(), null)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(exams, assessments);
    }

    @Test
    void refusesTheWrongTaskBeforeCreatingADraft() {
        assertThatThrownBy(() -> service.startAssessment(sitting.getId(), FullTest.Slot.WRITING_TASK_1,
                UUID.randomUUID(), null, null)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(assessments);
    }

    @Test
    void retryResumesTheExactBoundDraft() {
        var binding = FullTestComponentAttempt.reserve(sitting.getId(), "WRITING_TASK_1");
        UUID answer = UUID.randomUUID();
        binding.bind(answer);
        when(bindings.findBySittingIdAndSlot(sitting.getId(), "WRITING_TASK_1")).thenReturn(Optional.of(binding));
        when(assessments.start(test.taskId(FullTest.Slot.WRITING_TASK_1), binding.getId(), null, null, answer))
                .thenReturn(new FullTestAssessmentStarter.Prepared(answer, null));
        assertThat(service.startAssessment(sitting.getId(), FullTest.Slot.WRITING_TASK_1,
                test.taskId(FullTest.Slot.WRITING_TASK_1), null, null).attemptId()).isEqualTo(answer);
    }

    @Test
    void sharedTaskDefinitionsGetDifferentAttemptKeysForDifferentSittings() {
        var second = FullTestSitting.start(test.getId(), user, now.minusSeconds(30));
        when(sittings.findByIdForUpdate(second.getId())).thenReturn(Optional.of(second));
        var keys = new ArrayList<UUID>();
        when(assessments.start(any(), any(), isNull(), isNull(), isNull())).thenAnswer(c -> {
            keys.add(c.getArgument(1));
            return new FullTestAssessmentStarter.Prepared(UUID.randomUUID(), null);
        });
        var first = service.startAssessment(sitting.getId(), FullTest.Slot.WRITING_TASK_1,
                test.taskId(FullTest.Slot.WRITING_TASK_1), null, null);
        var other = service.startAssessment(second.getId(), FullTest.Slot.WRITING_TASK_1,
                test.taskId(FullTest.Slot.WRITING_TASK_1), null, null);
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(first.attemptId()).isNotEqualTo(other.attemptId());
    }

    @Test
    void cannotSilentlyResumeAnUnrelatedPaper() {
        when(attempts.findFirstByUserIdAndExamIdAndModeAndStatusOrderByStartedAtDesc(any(), any(), any(), any()))
                .thenReturn(Optional.of(mock(ExamAttempt.class)));
        assertThatThrownBy(() -> service.startPaper(sitting.getId(),
                com.englow3.exam.dto.command.StartExamAttemptCommand.OpenAttempt.RESUME))
                        .isInstanceOf(ConflictException.class);
        verifyNoInteractions(exams);
    }
}
