package com.englow3.assessment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.assessment.entity.*;
import com.englow3.assessment.repository.*;
import com.englow3.assessment.helper.AssessmentResultMapper;
import com.englow3.assessment.service.impl.AssessmentServiceImpl;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.*;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {
    @Mock
    AssessmentTaskRepository tasks;
    @Mock
    AssessmentAttemptRepository attempts;
    @Mock
    AssessmentResultMapper results;
    @Mock
    UserDirectory users;
    @Mock
    AiJobQueue queue;
    @Mock
    ObjectStorageClient storage;
    @Mock
    TransactionTemplate tx;
    AssessmentServiceImpl service;
    final UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    AssessmentAttempt attempt;

    @BeforeEach
    void setup() {
        service = new AssessmentServiceImpl(tasks, results, attempts, users, queue, new ObjectMapper(), storage, tx,
                Clock.systemUTC());
        when(users.requireCurrentUserId()).thenReturn(user);
        when(tx.execute(any())).thenAnswer(
                i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        attempt = AssessmentAttempt.draft(id, user, UUID.randomUUID(), UUID.randomUUID(), AssessmentSkill.WRITING, "{}",
                null, null, null);
    }

    @Test
    void preventsCrossUserAccess() {
        when(attempts.findById(id)).thenReturn(Optional.of(AssessmentAttempt.draft(id, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), AssessmentSkill.WRITING, "{}", null, null, null)));
        assertThrows(NotFoundException.class, () -> service.attempt(id));
    }

    @Test
    void rejectsTooShortEssay() {
        when(attempts.findById(id)).thenReturn(Optional.of(attempt));
        when(attempts.lockById(id)).thenReturn(Optional.of(attempt));
        assertThrows(BadRequestException.class, () -> service.submit(id));
        verifyNoInteractions(queue);
    }

    @Test
    void sendsToHumanWhenAiDisabled() {
        attempt.saveAnswer("word ".repeat(25), 0);
        when(attempts.findById(id)).thenReturn(Optional.of(attempt));
        when(attempts.lockById(id)).thenReturn(Optional.of(attempt));
        when(attempts.saveAndFlush(attempt)).thenReturn(attempt);
        service.submit(id);
        assertEquals(AssessmentAttemptStatus.NEEDS_REVIEW, attempt.getStatus());
        verifyNoInteractions(queue);
    }

    @Test
    void queuesOnceOnDuplicateSubmission() {
        attempt.saveAnswer("word ".repeat(25), 0);
        ReflectionTestUtils.setField(service, "aiEnabled", true);
        ReflectionTestUtils.setField(service, "automaticWriting", true);
        when(attempts.findById(id)).thenReturn(Optional.of(attempt));
        when(attempts.lockById(id)).thenReturn(Optional.of(attempt));
        when(attempts.saveAndFlush(attempt)).thenReturn(attempt);
        when(queue.hasDailyAllowance(user)).thenReturn(true);
        service.submit(id);
        service.submit(id);
        verify(queue, times(1)).enqueueProductiveAssessment(eq(id), anyString(), eq("productive:" + id + ":1"),
                eq("productive-v1"), eq(user));
    }

    @Test
    void quotaRejectionKeepsDraft() {
        attempt.saveAnswer("word ".repeat(25), 0);
        ReflectionTestUtils.setField(service, "aiEnabled", true);
        ReflectionTestUtils.setField(service, "automaticWriting", true);
        when(attempts.findById(id)).thenReturn(Optional.of(attempt));
        when(attempts.lockById(id)).thenReturn(Optional.of(attempt));
        when(queue.hasDailyAllowance(user)).thenReturn(false);
        assertThrows(ConflictException.class, () -> service.submit(id));
        assertEquals(AssessmentAttemptStatus.DRAFT, attempt.getStatus());
    }

    @Test
    void speakingSubmissionSealsAudioBeforeGradingAndDoesNotCopyAgain() {
        attempt = AssessmentAttempt.draft(id, user, UUID.randomUUID(), UUID.randomUUID(), AssessmentSkill.SPEAKING,
                "{}", "original.wav", "audio/wav", 100L);
        when(attempts.findById(id)).thenReturn(Optional.of(attempt));
        when(attempts.lockById(id)).thenReturn(Optional.of(attempt));
        when(attempts.saveAndFlush(attempt)).thenReturn(attempt);
        when(storage.metadata(any(), anyString()))
                .thenReturn(new ObjectStorageClient.StoredObjectMetadata(100L, "audio/wav"));
        service.submit(id);
        String sealed = attempt.getAudioObjectKey();
        assertTrue(sealed.startsWith("productive-sealed/"));
        assertEquals(AssessmentAttemptStatus.NEEDS_REVIEW, attempt.getStatus());
        service.submit(id);
        verify(storage, times(1)).copy(any(), eq("original.wav"), eq(sealed));
        verifyNoInteractions(queue);
    }

    @Test
    void recordingChangedDuringCopyKeepsDraftAndDeletesUnusedSnapshot() {
        attempt = AssessmentAttempt.draft(id, user, UUID.randomUUID(), UUID.randomUUID(), AssessmentSkill.SPEAKING,
                "{}", "original.wav", "audio/wav", 100L);
        when(attempts.findById(id)).thenReturn(Optional.of(attempt));
        when(storage.metadata(any(), eq("original.wav")))
                .thenReturn(new ObjectStorageClient.StoredObjectMetadata(100L, "audio/wav"));
        when(storage.metadata(any(), startsWith("productive-sealed/")))
                .thenReturn(new ObjectStorageClient.StoredObjectMetadata(200L, "audio/wav"));
        assertThrows(BadRequestException.class, () -> service.submit(id));
        assertEquals("original.wav", attempt.getAudioObjectKey());
        assertEquals(AssessmentAttemptStatus.DRAFT, attempt.getStatus());
        verify(storage).delete(any(), startsWith("productive-sealed/"));
        verifyNoInteractions(queue);
    }
}
