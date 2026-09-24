package com.englow3.speaking.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.service.AiJobQueue;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.speaking.dto.result.SpeakingAttemptResult;
import com.englow3.speaking.dto.result.SpeakingPromptResult;
import com.englow3.speaking.dto.result.SpeakingUploadTicket;
import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingAttemptWord;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.entity.SpeakingPromptStatus;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingAttemptWordRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** Practising: what there is to say, recording it, and getting the score back. */
@Service
@RequiredArgsConstructor
public class SpeakingService {

    /** The type name this module's jobs carry. The queue stores it as text and never interprets it. */
    static final String ATTEMPT_TARGET_TYPE = "SPEAKING_ATTEMPT";

    /**
     * Bumped when the request sent to the assessment changes shape. Stored on every job so an old result can be told
     * apart from one produced under the current rules.
     */
    static final String ASSESSMENT_VERSION = "speech-assessment-v1";

    private final SpeakingPromptRepository promptRepo;
    private final SpeakingAttemptRepository attemptRepo;
    private final SpeakingAttemptWordRepository wordRepo;
    private final AiJobQueue aiJobQueue;
    private final UserDirectory userDirectory;
    private final ObjectStorageClient objectStorage;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.speaking-bucket}")
    private String speakingBucket;

    @Value("${app.storage.speaking-upload-url-ttl:PT15M}")
    private Duration uploadUrlTtl;

    @Value("${app.storage.speaking-media-url-ttl:PT3H}")
    private Duration playbackUrlTtl;

    @Value("${app.speech.locale:en-US}")
    private String locale;

    @Transactional(readOnly = true)
    public Page<SpeakingPromptResult> searchPublished(String category, String title, Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<SpeakingPrompt> page = promptRepo.searchByStatus(SpeakingPromptStatus.PUBLISHED, category, title,
                pageable);
        Map<UUID, BigDecimal> bestScores = attemptRepo.bestScores(userId,
                page.getContent().stream().map(SpeakingPrompt::getId).toList());

        return page.map(prompt -> SpeakingPromptResult.of(prompt, bestScores.get(prompt.getId())));
    }

    @Transactional(readOnly = true)
    public SpeakingPromptResult promptDetail(UUID promptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        SpeakingPrompt prompt = requirePublishedPrompt(promptId);

        return SpeakingPromptResult.of(prompt, attemptRepo.bestScores(userId, List.of(promptId)).get(promptId));
    }

    /**
     * Opens an attempt and hands back somewhere to put the recording.
     * <p>
     * The row is written before the audio exists so the upload has something to belong to. The browser then uploads
     * straight to object storage: routing tens of seconds of audio through this application would tie up a request
     * thread for the length of the learner's connection and buy nothing, since the file is going to storage either way.
     */
    @Transactional
    public SpeakingUploadTicket startAttempt(UUID promptId, String contentType, long contentLength) {
        SpeakingAudioKeys.requireAcceptedContentType(contentType);
        SpeakingAudioKeys.requireAcceptedSize(contentLength);

        UUID userId = userDirectory.requireCurrentUserId();
        requirePublishedPrompt(promptId);

        UUID attemptId = UUID.randomUUID();
        String objectKey = SpeakingAudioKeys.audioKey(userId, attemptId, contentType);
        SpeakingAttempt attempt = attemptRepo
                .save(SpeakingAttempt.awaitingUpload(userId, promptId, objectKey, contentType));

        // Short-lived, and bound to this exact size. A presigned PUT never reaches this application, so once the URL
        // is out the signature is the only thing standing between it and a file of any size at all.
        String uploadUrl = objectStorage.presignPut(speakingBucket, objectKey, contentType, contentLength, uploadUrlTtl)
                .toString();

        return new SpeakingUploadTicket(attempt.getId(), uploadUrl, contentType, uploadUrlTtl.getSeconds());
    }

    /**
     * The learner says the upload finished, so the assessment is queued.
     * <p>
     * Storage is asked whether the object is really there first. A client that failed its upload and submitted anyway
     * would otherwise produce a job that fails at the provider - slower, and with a worse message than "your recording
     * never arrived".
     */
    @Transactional
    public SpeakingAttemptResult submitAttempt(UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        SpeakingAttempt attempt = requireOwnAttempt(attemptId, userId);
        SpeakingPrompt prompt = promptRepo.findById(attempt.getSpeakingPromptId())
                .orElseThrow(() -> promptNotFound(attempt.getSpeakingPromptId()));

        requireUploadedAudio(attempt);
        requireQuotaRemaining(userId);
        attempt.markQueued();

        aiJobQueue.enqueue(AiJobType.SPEECH_ASSESSMENT, ATTEMPT_TARGET_TYPE, attempt.getId(),
                assessmentRequest(attempt, prompt),
                // One job per attempt, whatever the client does. A double submit loses at the unique index rather
                // than paying the provider twice for the same recording.
                ATTEMPT_TARGET_TYPE + ":" + attempt.getId(), ASSESSMENT_VERSION, userId);

        return result(attempt, prompt, List.of());
    }

    /** What the screen polls while it waits, and reads once the score lands. */
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

        // No word breakdown: the history list shows scores, and loading every word of every attempt to draw a row of
        // numbers would be a query per attempt for something nobody looks at until they open one.
        return attemptRepo.findByUserIdAndSpeakingPromptIdOrderByCreatedAtDesc(userId, promptId).stream()
                .map(attempt -> result(attempt, prompt, List.of())).toList();
    }

    private SpeakingAttemptResult result(SpeakingAttempt attempt, SpeakingPrompt prompt,
            List<SpeakingAttemptWord> words) {
        // Signed fresh on every read rather than stored: a URL kept in the row would be a URL that stops working.
        String audioUrl = objectStorage.presignGet(speakingBucket, attempt.getAudioObjectKey(), playbackUrlTtl)
                .toString();

        return SpeakingAttemptResult.of(attempt, prompt, audioUrl, words);
    }

    /** The request as it will be sent, stored on the job so an audit can answer what exactly was asked for. */
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
            // Every value above is a String this method just put in; there is nothing here Jackson can refuse.
            throw new IllegalStateException("Could not serialise a speech assessment request", impossible);
        }
    }

    private void requireUploadedAudio(SpeakingAttempt attempt) {
        try {
            objectStorage.metadata(speakingBucket, attempt.getAudioObjectKey());
        } catch (RuntimeException missing) {
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

    /**
     * Checked at submission rather than when the attempt opens. Opening one costs nothing, and refusing a learner
     * before they have recorded anything would spend their effort only to tell them no.
     */
    private void requireQuotaRemaining(UUID userId) {
        // The budget is counted by the queue, across every feature that spends it - not here, where only this
        // module's own work would show and the tutor's would not.
        if (!aiJobQueue.hasDailyAllowance(userId)) {
            throw new ConflictException("SPEAKING_DAILY_LIMIT_REACHED",
                    "You have reached today's limit of %d AI requests".formatted(aiJobQueue.dailyRequestLimit()));
        }
    }

    private static NotFoundException promptNotFound(UUID promptId) {
        return new NotFoundException("SPEAKING_PROMPT_NOT_FOUND", "No speaking prompt with id %s".formatted(promptId));
    }
}
