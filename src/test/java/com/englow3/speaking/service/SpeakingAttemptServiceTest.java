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

import software.amazon.awssdk.services.s3.model.S3Exception;

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

class SpeakingAttemptServiceTest {

    private static final int DAILY_LIMIT = 3;

    private final SpeakingPromptRepository promptRepo = mock(SpeakingPromptRepository.class);
    private final SpeakingAttemptRepository attemptRepo = mock(SpeakingAttemptRepository.class);
    private final SpeakingAttemptWordRepository wordRepo = mock(SpeakingAttemptWordRepository.class);
    private final AiJobQueue aiJobQueue = mock(AiJobQueue.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);

    private final SpeakingAttemptService service = new com.englow3.speaking.service.impl.SpeakingAttemptServiceImpl(
            promptRepo, attemptRepo, wordRepo, aiJobQueue, userDirectory, objectStorage, presignedUrls,
            new ObjectMapper());

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

        verify(aiJobQueue).enqueueSpeechAssessment(eq(attempt.getId()), anyString(), anyString(), anyString(),
                eq(userId));
    }

    @Test
    void refusesOnceTodaysAssessmentsAreSpent() {
        SpeakingAttempt attempt = uploadedAttempt();
        when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitAttempt(attempt.getId())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_DAILY_LIMIT_REACHED");

        verify(aiJobQueue, never()).enqueueSpeechAssessment(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void refusesWhenTheRecordingNeverArrived() {
        SpeakingAttempt attempt = uploadedAttempt();
        when(objectStorage.metadata(anyString(), anyString()))
                .thenThrow(S3Exception.builder().statusCode(404).message("no such key").build());

        assertThatThrownBy(() -> service.submitAttempt(attempt.getId())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_RECORDING_MISSING");

        verify(aiJobQueue, never()).enqueueSpeechAssessment(any(), anyString(), anyString(), anyString(), any());
    }

    /**
     * A store that is down is not a missing recording. Saying it was sent the learner off to record again, when the
     * recording was fine and only needed the store to come back.
     */
    @Test
    void doesNotBlameTheLearnerWhenTheStoreIsDown() {
        SpeakingAttempt attempt = uploadedAttempt();
        when(objectStorage.metadata(anyString(), anyString()))
                .thenThrow(S3Exception.builder().statusCode(503).message("slow down").build());

        assertThatThrownBy(() -> service.submitAttempt(attempt.getId())).isInstanceOf(S3Exception.class);
        verify(aiJobQueue, never()).enqueueSpeechAssessment(any(), anyString(), anyString(), anyString(), any());
    }

    /** A second submit of one already queued is "already submitted", even for a learner who has spent the day. */
    @Test
    void saysAlreadySubmittedBeforeSayingTheLimitIsReached() {
        SpeakingAttempt attempt = uploadedAttempt();
        attempt.markQueued();
        when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitAttempt(attempt.getId())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_ATTEMPT_NOT_AWAITING_UPLOAD");
    }

    @Test
    void refusesToReadAnAttemptBelongingToSomebodyElse() {
        UUID otherAttempt = UUID.randomUUID();
        when(attemptRepo.findByIdAndUserId(otherAttempt, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attemptResult(otherAttempt)).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("SPEAKING_ATTEMPT_NOT_FOUND");
    }

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
