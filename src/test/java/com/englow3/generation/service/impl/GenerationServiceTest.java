package com.englow3.generation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.generation.entity.GenerationKind;
import com.englow3.generation.entity.GenerationRequest;
import com.englow3.generation.repository.GenerationRequestRepository;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.user.api.UserDirectory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Orchestration of starting and resuming: which limits are asked, against which day, and that nothing is queued when
 * one refuses. Whether a request may be resumed at all is {@link GenerationRequest}'s rule and has its own test; the
 * entity is a mock here, so only the service's own branches are checked.
 */
class GenerationServiceTest {

    private static final int DAILY_LIMIT = 3;

    private final GenerationRequestRepository requestRepo = mock(GenerationRequestRepository.class);
    private final GenerationSteps steps = mock(GenerationSteps.class);
    private final AiJobQueue queue = mock(AiJobQueue.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final StudyCalendar calendar = mock(StudyCalendar.class);
    private final Instant now = Instant.parse("2026-11-26T10:00:00Z");
    private final Instant startOfToday = Instant.parse("2026-11-25T17:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final GenerationServiceImpl service = new GenerationServiceImpl(requestRepo, steps, queue, users, calendar,
            clock, JsonMapper.builderWithJackson2Defaults().build());

    private final UUID author = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final GenerationRequest failed = mock(GenerationRequest.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dailyLimit", DAILY_LIMIT);
        when(calendar.startOfToday()).thenReturn(startOfToday);
        when(users.requireCurrentUserId()).thenReturn(author);
        when(users.displayNames(anyList())).thenReturn(Map.of(author, "Author"));
        when(requestRepo.save(any(GenerationRequest.class))).thenAnswer(call -> call.getArgument(0));

        when(failed.getId()).thenReturn(requestId);
        when(failed.getRequestedByUserId()).thenReturn(author);
        when(requestRepo.findById(requestId)).thenReturn(Optional.of(failed));
        when(requestRepo.findByIdForUpdate(requestId)).thenReturn(Optional.of(failed));
    }

    @Test
    void aRequestIsRefusedOnceTodaysLimitIsUsed() {
        when(requestRepo.countCountedSince(author, startOfToday)).thenReturn((long) DAILY_LIMIT);

        assertThatThrownBy(() -> service.request(GenerationKind.IELTS_FULL_TEST, null))
                .hasFieldOrPropertyWithValue("code", "GENERATION_DAILY_LIMIT");
        verify(requestRepo, never()).save(any());
        verify(steps, never()).enqueue(any());
    }

    @Test
    void theLimitComesFromConfiguration() {
        ReflectionTestUtils.setField(service, "dailyLimit", 5);
        when(requestRepo.countCountedSince(author, startOfToday)).thenReturn(4L);

        service.request(GenerationKind.IELTS_FULL_TEST, null);

        verify(steps).enqueue(any(GenerationRequest.class));
        assertThat(service.allowance().limit()).isEqualTo(5);
    }

    /**
     * The hole this closes: a failed request made on an earlier day used to skip the daily limit when resumed, and then
     * counted on no day. Now the limit is asked whatever day the request was made, and the service never looks at it.
     */
    @Test
    void resumingAsksTodaysLimitWhateverDayTheRequestWasMade() {
        when(requestRepo.countCountedSince(author, startOfToday)).thenReturn((long) DAILY_LIMIT);

        assertThatThrownBy(() -> service.resume(requestId)).hasFieldOrPropertyWithValue("code",
                "GENERATION_DAILY_LIMIT");
        verify(failed, never()).getCreatedAt();
        verify(failed, never()).resume(any());
        verify(steps, never()).enqueue(any());
    }

    @Test
    void resumingWithRoomTodayResumesUnderTheRequestersLockAndQueuesTheStep() {
        when(requestRepo.countCountedSince(author, startOfToday)).thenReturn((long) DAILY_LIMIT - 1);

        service.resume(requestId);

        InOrder order = inOrder(requestRepo, failed, steps);
        order.verify(requestRepo).lockUser("generation:" + author);
        order.verify(failed).requireResumable();
        order.verify(requestRepo).countCountedSince(author, startOfToday);
        order.verify(failed).resume(now);
        order.verify(requestRepo).saveAndFlush(failed);
        order.verify(steps).enqueue(failed);
    }

    @Test
    void resumingIsRefusedWhileAnotherOfTheRequestersIsRunning() {
        when(requestRepo.existsByRequestedByUserIdAndStatusIn(eq(author), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.resume(requestId)).hasFieldOrPropertyWithValue("code",
                "GENERATION_IN_PROGRESS");
        verify(failed, never()).resume(any());
        verify(steps, never()).enqueue(any());
    }

    @Test
    void resumingAnUnknownRequestIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(requestRepo.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resume(unknown)).hasFieldOrPropertyWithValue("code",
                "GENERATION_REQUEST_NOT_FOUND");
        verify(requestRepo, never()).lockUser(anyString());
    }
}
