package com.englow3.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTask;
import com.englow3.assessment.helper.AssessmentResultMapper;
import com.englow3.assessment.query.AssessmentQueueQuery;
import com.englow3.assessment.query.AssessmentWorkloadQuery;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.repository.AssessmentReviewRepository;
import com.englow3.assessment.repository.AssessmentTaskRepository;
import com.englow3.assessment.service.impl.AssessmentAuthoringServiceImpl;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssessmentGradingTest {

    private final AssessmentTaskRepository taskRepo = mock(AssessmentTaskRepository.class);
    private final AssessmentAttemptRepository attemptRepo = mock(AssessmentAttemptRepository.class);
    private final AssessmentReviewRepository reviewRepo = mock(AssessmentReviewRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    private final AssessmentAuthoringService service = new AssessmentAuthoringServiceImpl(taskRepo, attemptRepo,
            reviewRepo, mock(AssessmentWorkloadQuery.class), mock(AssessmentQueueQuery.class), users, currentUser,
            mock(AssessmentResultMapper.class), new ObjectMapper(), Clock.systemUTC(), tx);

    /** Staff can sit any published task, their own included; marking that work would be grading themselves. */
    @Test
    void aReviewerCannotGradeTheirOwnSubmission() {
        UUID staff = UUID.randomUUID();
        AssessmentTask task = AssessmentTask.draft(new AssessmentTaskCommand(AssessmentSkill.WRITING, "Opinion",
                "TASK_2", "Discuss.", "Use examples.", "Sample", 250, 2400), staff);
        AssessmentAttempt attempt = AssessmentAttempt.draft(UUID.randomUUID(), staff, task.getId(), UUID.randomUUID(),
                AssessmentSkill.WRITING, "{}", null, null, null);
        attempt.submit(false, Instant.now());
        when(users.requireCurrentUserId()).thenReturn(staff);
        when(tx.execute(any())).thenAnswer(
                i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        when(attemptRepo.lockById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(taskRepo.lockById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.grade(attempt.getId(), "{}", "Reviewed", null))
                .isInstanceOf(ForbiddenException.class).extracting(e -> ((ForbiddenException) e).getCode())
                .isEqualTo("REVIEW_OWN_SUBMISSION");

        assertThat(attempt.getStatus()).isEqualTo(AssessmentAttemptStatus.NEEDS_REVIEW);
        verify(reviewRepo, never()).save(any());
    }
}
