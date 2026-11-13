package com.englow3.speaking.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.amazon.awssdk.services.s3.model.S3Exception;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.speaking.dto.result.SpeakingAttemptResult;
import com.englow3.speaking.dto.result.SpeakingUploadTicket;
import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingAttemptStatus;
import com.englow3.speaking.entity.SpeakingAttemptWord;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.entity.SpeakingPromptStatus;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingAttemptWordRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.speaking.service.SpeakingAttemptService;
import com.englow3.speaking.service.SpeakingAudioKeys;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Recording upload, assessment queueing, and result lifecycle. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakingAttemptServiceImpl implements SpeakingAttemptService {

    static final String ATTEMPT_TARGET_TYPE = "SPEAKING_ATTEMPT";
    static final String ASSESSMENT_VERSION = "speech-assessment-v1";

    /** Far more takes than anyone records by hand in ten minutes; few enough that a script cannot fill the bucket. */
    static final int MAX_STARTS_PER_WINDOW = 30;
    static final Duration START_WINDOW = Duration.ofMinutes(10);

    /** Long past the fifteen minutes an upload URL lasts, so nothing still in progress is ever swept. */
    static final Duration ABANDONED_UPLOAD_AGE = Duration.ofHours(24);

    private final SpeakingPromptRepository promptRepo;
    private final SpeakingAttemptRepository attemptRepo;
    private final SpeakingAttemptWordRepository wordRepo;
    private final AiJobQueue aiJobQueue;
    private final UserDirectory userDirectory;
    private final ObjectStorageClient objectStorage;
    private final PresignedUrlResolver presignedUrls;
    private final ObjectMapper objectMapper;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    @Value("${app.storage.speaking-bucket}")
    private String speakingBucket;

    @Value("${app.storage.speaking-upload-url-ttl:PT15M}")
    private Duration uploadUrlTtl;

    @Value("${app.storage.speaking-media-url-ttl:PT3H}")
    private Duration playbackUrlTtl;

    @Value("${app.speech.locale:en-US}")
    private String locale;

    @Transactional
    public SpeakingUploadTicket startAttempt(UUID promptId, String contentType, long contentLength) {
        SpeakingAudioKeys.requireAcceptedContentType(contentType);
        SpeakingAudioKeys.requireAcceptedSize(contentLength);

        UUID userId = userDirectory.requireCurrentUserId();
        requirePublishedPrompt(promptId);
        // Asked again at submission, but first here, as the tutor does: a learner out of requests would otherwise
        // record and upload only to be told the recording cannot be assessed today.
        requireQuotaRemaining(userId);
        // Every start signs a URL for up to 10 MB in the bucket, and nothing removes an upload no submission claims.
        rateLimiter.check(userId, "speaking-start", MAX_STARTS_PER_WINDOW, START_WINDOW);

        UUID attemptId = UUID.randomUUID();
        String objectKey = SpeakingAudioKeys.audioKey(userId, attemptId, contentType);
        SpeakingAttempt attempt = attemptRepo
                .save(SpeakingAttempt.awaitingUpload(userId, promptId, objectKey, contentType));
        String uploadUrl = objectStorage.presignPut(speakingBucket, objectKey, contentType, contentLength, uploadUrlTtl)
                .toString();

        return new SpeakingUploadTicket(attempt.getId(), uploadUrl, contentType, uploadUrlTtl.getSeconds());
    }

    @Transactional
    public SpeakingAttemptResult submitAttempt(UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        SpeakingAttempt attempt = requireOwnAttempt(attemptId, userId);
        SpeakingPrompt prompt = promptRepo.findById(attempt.getSpeakingPromptId())
                .orElseThrow(() -> promptNotFound(attempt.getSpeakingPromptId()));

        // The attempt's own state first: a second submit of one already queued is "already submitted", not "you are
        // out of requests", and the upload check would only cost a round trip to the bucket to say so.
        attempt.markQueued();
        requireUploadedAudio(attempt);
        requireQuotaRemaining(userId);

        aiJobQueue.enqueueSpeechAssessment(attempt.getId(), assessmentRequest(attempt, prompt),
                ATTEMPT_TARGET_TYPE + ":" + attempt.getId(), ASSESSMENT_VERSION, userId);

        return result(attempt, prompt, List.of());
    }

    @Transactional(readOnly = true)
    public SpeakingAttemptResult attemptResult(UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        SpeakingAttempt attempt = requireOwnAttempt(attemptId, userId);
        SpeakingPrompt prompt = promptRepo.findById(attempt.getSpeakingPromptId())
                .orElseThrow(() -> promptNotFound(attempt.getSpeakingPromptId()));

        return result(attempt, prompt, wordRepo.findBySpeakingAttemptIdOrderByOrderNo(attempt.getId()));
    }

    @Transactional(readOnly = true)
    public List<SpeakingAttemptResult> attemptHistory(UUID promptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        SpeakingPrompt prompt = requirePublishedPrompt(promptId);
        return attemptRepo.findByUserIdAndSpeakingPromptIdOrderByCreatedAtDesc(userId, promptId).stream()
                .map(attempt -> result(attempt, prompt, List.of())).toList();
    }

    /**
     * An attempt still waiting for its recording a day after it was started will never get one: the upload URL lasted
     * fifteen minutes, and the screen submits as soon as the upload finishes. Each such attempt held a row and, if the
     * upload did happen, ten megabytes nobody would ever read.
     */
    @Override
    @Transactional
    public int removeAbandonedUploads() {
        int removed = 0;
        for (SpeakingAttempt attempt : attemptRepo.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                SpeakingAttemptStatus.AWAITING_UPLOAD, clock.instant().minus(ABANDONED_UPLOAD_AGE))) {
            if (attemptRepo.deleteIfAwaitingUpload(attempt.getId()) == 0) {
                continue;
            }
            removed++;
            try {
                objectStorage.delete(speakingBucket, attempt.getAudioObjectKey());
            } catch (RuntimeException failure) {
                // The row is what anyone reads; a file left behind is a stray in the bucket, not a broken attempt.
                log.warn("Could not remove the upload of abandoned speaking attempt {}", attempt.getId(), failure);
            }
        }
        return removed;
    }

    private SpeakingAttemptResult result(SpeakingAttempt attempt, SpeakingPrompt prompt,
            List<SpeakingAttemptWord> words) {
        String audioUrl = presignedUrls.resolve(speakingBucket, attempt.getAudioObjectKey(), playbackUrlTtl);
        return SpeakingAttemptResult.of(attempt, prompt, audioUrl, words);
    }

    private String assessmentRequest(SpeakingAttempt attempt, SpeakingPrompt prompt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("speakingAttemptId", attempt.getId().toString());
        payload.put("objectKey", attempt.getAudioObjectKey());
        payload.put("contentType", attempt.getAudioContentType());
        payload.put("locale", locale);
        payload.put("referenceText", prompt.getReferenceText());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Could not serialise a speech assessment request", impossible);
        }
    }

    private void requireUploadedAudio(SpeakingAttempt attempt) {
        try {
            objectStorage.metadata(speakingBucket, attempt.getAudioObjectKey());
        } catch (S3Exception notThere) {
            // Only "no such object" means the learner has not uploaded. Anything else is the store failing, and
            // reporting that as a missing recording sent learners back to record again for nothing.
            if (notThere.statusCode() != 404) {
                throw notThere;
            }
            throw new ConflictException("SPEAKING_RECORDING_MISSING",
                    "The recording for this attempt has not been uploaded");
        }
    }

    private SpeakingAttempt requireOwnAttempt(UUID attemptId, UUID userId) {
        return attemptRepo.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("SPEAKING_ATTEMPT_NOT_FOUND",
                        "No speaking attempt with id %s".formatted(attemptId)));
    }

    private SpeakingPrompt requirePublishedPrompt(UUID promptId) {
        return promptRepo.findById(promptId).filter(prompt -> prompt.getStatus() == SpeakingPromptStatus.PUBLISHED)
                .orElseThrow(() -> promptNotFound(promptId));
    }

    private void requireQuotaRemaining(UUID userId) {
        if (!aiJobQueue.hasDailyAllowance(userId)) {
            throw new ConflictException("SPEAKING_DAILY_LIMIT_REACHED",
                    "You have reached today's limit of %d AI requests".formatted(aiJobQueue.dailyRequestLimit(userId)));
        }
    }

    private static NotFoundException promptNotFound(UUID promptId) {
        return new NotFoundException("SPEAKING_PROMPT_NOT_FOUND", "No speaking prompt with id %s".formatted(promptId));
    }
}
