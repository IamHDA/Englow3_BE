package com.englow3.speaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingAttemptWordRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What has to be true before a recording is accepted, and before anyone is charged for scoring it. */
class SpeakingServiceTest {

    private static final int DAILY_LIMIT = 3;

    private final SpeakingPromptRepository promptRepo = mock(SpeakingPromptRepository.class);
    private final SpeakingAttemptRepository attemptRepo = mock(SpeakingAttemptRepository.class);
    private final SpeakingAttemptWordRepository wordRepo = mock(SpeakingAttemptWordRepository.class);
    private final AiJobQueue aiJobQueue = mock(AiJobQueue.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);

    private final SpeakingService service = new com.englow3.speaking.service.impl.SpeakingServiceImpl(promptRepo,
            attemptRepo, wordRepo, aiJobQueue, userDirectory, objectStorage, presignedUrls, new ObjectMapper());

    private final UUID userId = UUID.randomUUID();
    private SpeakingPrompt prompt;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "speakingBucket", "speaking");
        ReflectionTestUtils.setField(service, "uploadUrlTtl", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(service, "playbackUrlTtl", Duration.ofHours(3));
        ReflectionTestUtils.setField(service, "locale", "en-US");

        prompt = SpeakingPrompt.draft("seat-sit", "Seat vs sit", "Minimal Pairs", "A2", "Please sit on this seat.",
                null, null, "/iː/ vs /ɪ/", "[]", UUID.randomUUID());
        prompt.publish(Instant.now());

        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(promptRepo.findById(prompt.getId())).thenReturn(Optional.of(prompt));
        when(attemptRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // The budget is the queue's to count now; by default there is room left.
        when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(true);
        when(aiJobQueue.dailyRequestLimit()).thenReturn(DAILY_LIMIT);
        when(objectStorage.presignPut(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://storage.example/put").toURL());
        when(presignedUrls.resolve(anyString(), anyString(), any())).thenReturn("https://storage.example/get");
    }

    private SpeakingAttempt uploadedAttempt() {
        SpeakingAttempt attempt = SpeakingAttempt.awaitingUpload(userId, prompt.getId(), "speaking/u/a.wav",
                "audio/wav");
        when(attemptRepo.findByIdAndUserId(attempt.getId(), userId)).thenReturn(Optional.of(attempt));
        return attempt;
    }

    @Test
    void issuesAnUploadUrlBoundToTheDeclaredSize() {
        var ticket = service.startAttempt(prompt.getId(), "audio/wav", 480_000);

        assertThat(ticket.uploadUrl()).isEqualTo("https://storage.example/put");
        verify(objectStorage).presignPut(eq("speaking"), anyString(), eq("audio/wav"), eq(480_000L), any());
    }

    /** Refused before a URL exists, because a presigned PUT never comes back through here to be checked. */
    @Test
    void refusesASizeLargerThanCouldEverBeScored() {
        assertThatThrownBy(() -> service.startAttempt(prompt.getId(), "audio/wav", 50L * 1024 * 1024))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("SPEAKING_RECORDING_TOO_LARGE");

        verify(objectStorage, never()).presignPut(anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void refusesAFormatTheAssessmentCannotRead() {
        assertThatThrownBy(() -> service.startAttempt(prompt.getId(), "audio/mpeg", 1000))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("SPEAKING_UNSUPPORTED_AUDIO_TYPE");
    }

    @Test
    void queuesAnAssessmentOnceTheRecordingHasArrived() {
        SpeakingAttempt attempt = uploadedAttempt();

        service.submitAttempt(attempt.getId());

        // Attributed to the learner: the attribution is what the daily budget counts, so an unattributed job would
        // be free.
        verify(aiJobQueue).enqueueSpeechAssessment(eq(attempt.getId()), anyString(), anyString(), anyString(),
                eq(userId));
    }

    /**
     * The check that stops an unbounded provider bill. It runs at submission, not when the attempt opens - opening one
     * costs nothing, and refusing before the learner has recorded would spend their effort to say no.
     */
    @Test
    void refusesOnceTodaysAssessmentsAreSpent() {
        SpeakingAttempt attempt = uploadedAttempt();
        when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitAttempt(attempt.getId())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_DAILY_LIMIT_REACHED");

        verify(aiJobQueue, never()).enqueueSpeechAssessment(any(), anyString(), anyString(), anyString(), any());
    }

    /** A client that failed its upload and submitted anyway is told what actually went wrong. */
    @Test
    void refusesWhenTheRecordingNeverArrived() {
        SpeakingAttempt attempt = uploadedAttempt();
        when(objectStorage.metadata(anyString(), anyString())).thenThrow(new RuntimeException("no such key"));

        assertThatThrownBy(() -> service.submitAttempt(attempt.getId())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_RECORDING_MISSING");

        verify(aiJobQueue, never()).enqueueSpeechAssessment(any(), anyString(), anyString(), anyString(), any());
    }

    /**
     * Someone else's attempt answers exactly as one that does not exist. A different answer for each would make an
     * attempt id a way to find out whose recordings are there.
     */
    @Test
    void refusesToReadAnAttemptBelongingToSomebodyElse() {
        UUID otherAttempt = UUID.randomUUID();
        when(attemptRepo.findByIdAndUserId(otherAttempt, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attemptResult(otherAttempt)).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("SPEAKING_ATTEMPT_NOT_FOUND");
    }

    /** An unpublished prompt is not practisable, and says so the same way a missing one does. */
    @Test
    void refusesAnAttemptAtAnUnpublishedPrompt() {
        SpeakingPrompt draft = SpeakingPrompt.draft("draft", "Draft", "Minimal Pairs", "A2", "Say this.", null, null,
                null, "[]", UUID.randomUUID());
        when(promptRepo.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.startAttempt(draft.getId(), "audio/wav", 1000))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("SPEAKING_PROMPT_NOT_FOUND");
    }
}
