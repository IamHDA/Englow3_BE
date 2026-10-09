package com.englow3.assessment.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.assessment.dto.result.AssessmentAttemptResult;
import com.englow3.assessment.dto.result.AssessmentCapabilities;
import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.dto.result.AssessmentUploadResult;
import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTask;
import com.englow3.assessment.entity.AssessmentTaskStatus;
import com.englow3.assessment.helper.AssessmentResultMapper;
import com.englow3.assessment.helper.AssessmentRubric;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.repository.AssessmentTaskRepository;
import com.englow3.assessment.service.AssessmentService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {
    private final AssessmentTaskRepository taskRepo;
    private final AssessmentResultMapper resultMapper;
    private final AssessmentAttemptRepository attemptRepo;
    private final UserDirectory userDirectory;
    private final AiJobQueue queue;
    private final ObjectMapper mapper;
    private final ObjectStorageClient storage;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;
    @Value("${app.assessment.automatic-writing:false}")
    private boolean automaticWriting;
    @Value("${app.assessment.automatic-speaking:false}")
    private boolean automaticSpeaking;
    @Value("${app.storage.speaking-bucket:speaking}")
    private String bucket;

    public AssessmentCapabilities capabilities() {
        return new AssessmentCapabilities(aiEnabled && automaticWriting, aiEnabled && automaticSpeaking, true);
    }

    public Page<AssessmentTaskResult> catalog(AssessmentSkill skill, Pageable page) {
        return transactionTemplate.execute(tx -> taskRepo.search(skill, AssessmentTaskStatus.PUBLISHED, null, page)
                .map(t -> AssessmentTaskResult.from(t, false)));
    }

    public AssessmentTaskResult task(UUID id) {
        return transactionTemplate.execute(tx -> AssessmentTaskResult.from(publishedTask(id), false));
    }

    public AssessmentUploadResult start(UUID taskId, UUID clientKey, String contentType, Long contentLength) {
        UUID user = userDirectory.requireCurrentUserId();
        AssessmentAttempt attempt = transactionTemplate.execute(tx -> {
            attemptRepo.lockRequest("assessment:" + user + ":" + clientKey);
            Optional<AssessmentAttempt> prior = attemptRepo.findByUserIdAndClientKey(user, clientKey);
            if (prior.isPresent()) {
                if (!prior.get().getTaskId().equals(taskId)) {
                    throw new ConflictException("REQUEST_REUSED", "This request belongs to another task");
                }
                return prior.get();
            }
            AssessmentTask task = publishedTask(taskId);
            UUID id = UUID.randomUUID();
            String key = null;
            if (task.getSkill() == AssessmentSkill.SPEAKING) {
                if (!"audio/wav".equals(contentType) || contentLength == null || contentLength < 45
                        || contentLength > 10485760) {
                    throw new BadRequestException("RECORDING_INVALID", "Upload WAV audio between 45 bytes and 10 MB");
                }
                key = "productive/" + user + "/" + id + ".wav";
            } else if (contentType != null || contentLength != null) {
                throw new BadRequestException("WRITING_AUDIO_INVALID", "Writing attempts do not contain audio");
            }
            return attemptRepo.saveAndFlush(AssessmentAttempt.draft(id, user, taskId, clientKey, task.getSkill(),
                    json(AssessmentTaskResult.from(task, true)), key, contentType, contentLength));
        });
        String uploadUrl = attempt.getStatus() == AssessmentAttemptStatus.DRAFT && attempt.getAudioObjectKey() != null
                ? storage.presignPut(bucket, attempt.getAudioObjectKey(), attempt.getAudioContentType(),
                        attempt.getAudioContentLength(), Duration.ofMinutes(15)).toString()
                : null;
        return new AssessmentUploadResult(result(attempt, false), uploadUrl);
    }

    public AssessmentAttemptResult saveDraft(UUID id, String text, long version) {
        UUID user = userDirectory.requireCurrentUserId();
        AssessmentAttempt a = transactionTemplate.execute(tx -> {
            AssessmentAttempt locked = owned(id, user, true);
            if (locked.getSkill() != AssessmentSkill.WRITING || text == null || text.length() > 12000) {
                throw new BadRequestException("ANSWER_INVALID", "Writing text must be at most 12000 characters");
            }
            locked.saveAnswer(text, version);
            return attemptRepo.saveAndFlush(locked);
        });
        return result(a, false);
    }

    public AssessmentAttemptResult submit(UUID id) {
        UUID user = userDirectory.requireCurrentUserId();
        AssessmentAttempt candidate = transactionTemplate.execute(tx -> owned(id, user, false));
        if (candidate.getStatus() != AssessmentAttemptStatus.DRAFT) {
            return result(candidate, false);
        }
        String sealedKey = candidate.getSkill() == AssessmentSkill.SPEAKING
                ? "productive-sealed/" + user + "/" + id + "/" + UUID.randomUUID() + ".wav"
                : null;
        if (sealedKey != null) {
            checkAudio(candidate);
            try {
                storage.copy(bucket, candidate.getAudioObjectKey(), sealedKey);
                var metadata = storage.metadata(bucket, sealedKey);
                if (metadata.contentLength() != candidate.getAudioContentLength()
                        || !"audio/wav".equals(metadata.contentType())) {
                    throw new BadRequestException("RECORDING_INVALID", "The recording changed during submission");
                }
            } catch (RuntimeException failure) {
                removeUnusedRecording(sealedKey);
                throw failure;
            }
        }
        AssessmentAttempt a;
        try {
            a = transactionTemplate.execute(tx -> {
                AssessmentAttempt locked = owned(id, user, true);
                if (locked.getStatus() != AssessmentAttemptStatus.DRAFT) {
                    return locked;
                }
                if (locked.getSkill() == AssessmentSkill.WRITING
                        && AssessmentRubric.wordCount(locked.getAnswerText()) < 20) {
                    throw new BadRequestException("ANSWER_TOO_SHORT", "Write at least 20 words before submitting");
                }
                boolean automatic = automatic(locked.getSkill());
                checkQuota(user, automatic);
                if (sealedKey != null) {
                    locked.sealRecording(sealedKey);
                }
                locked.submit(automatic, clock.instant());
                if (automatic) {
                    enqueue(locked);
                }
                return attemptRepo.saveAndFlush(locked);
            });
        } catch (RuntimeException failure) {
            if (sealedKey != null) {
                removeUnusedRecording(sealedKey);
            }
            throw failure;
        }
        if (sealedKey != null && !sealedKey.equals(a.getAudioObjectKey())) {
            removeUnusedRecording(sealedKey);
        }
        return result(a, false);
    }

    private void removeUnusedRecording(String key) {
        try {
            storage.delete(bucket, key);
        } catch (RuntimeException ignored) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not remove an unused recording snapshot");
        }
    }

    public AssessmentAttemptResult retry(UUID id) {
        UUID user = userDirectory.requireCurrentUserId();
        AssessmentAttempt a = transactionTemplate.execute(tx -> {
            AssessmentAttempt locked = owned(id, user, true);
            boolean automatic = automatic(locked.getSkill());
            checkQuota(user, automatic);
            locked.retry(automatic);
            if (automatic) {
                enqueue(locked);
            }
            return attemptRepo.saveAndFlush(locked);
        });
        return result(a, false);
    }

    public AssessmentAttemptResult requestReview(UUID id) {
        UUID user = userDirectory.requireCurrentUserId();
        AssessmentAttempt a = transactionTemplate.execute(tx -> {
            AssessmentAttempt locked = owned(id, user, true);
            locked.requestReview();
            return attemptRepo.saveAndFlush(locked);
        });
        return result(a, false);
    }

    public AssessmentAttemptResult attempt(UUID id) {
        UUID user = userDirectory.requireCurrentUserId();
        return result(transactionTemplate.execute(tx -> owned(id, user, false)), false);
    }

    public Page<AssessmentAttemptResult> history(UUID taskId, AssessmentSkill skill, AssessmentAttemptStatus status,
            String title, Pageable page) {
        UUID user = userDirectory.requireCurrentUserId();
        return transactionTemplate.execute(tx -> attemptRepo.filteredHistory(user, taskId, skill, status, title, page))
                .map(a -> result(a, false));
    }

    public Page<AssessmentAttemptResult> history(UUID taskId, Pageable page) {
        UUID user = userDirectory.requireCurrentUserId();
        return transactionTemplate.execute(tx -> attemptRepo.history(user, taskId, page)).map(a -> result(a, false));
    }

    private boolean automatic(AssessmentSkill skill) {
        return aiEnabled && (skill == AssessmentSkill.WRITING ? automaticWriting : automaticSpeaking);
    }

    private void checkQuota(UUID user, boolean automatic) {
        if (automatic && !queue.hasDailyAllowance(user)) {
            throw new ConflictException("ASSESSMENT_DAILY_LIMIT",
                    "Today's AI allowance has been reached. Save the draft and try tomorrow");
        }
    }

    private void enqueue(AssessmentAttempt a) {
        var payload = mapper.createObjectNode();
        payload.put("attemptId", a.getId().toString());
        payload.put("revision", a.getGradingRevision());
        payload.put("skill", a.getSkill().name());
        payload.put("taskSnapshot", a.getTaskSnapshot());
        payload.put("answerText", a.getAnswerText());
        payload.put("objectKey", a.getAudioObjectKey());
        payload.put("contentType", a.getAudioContentType());
        queue.enqueueProductiveAssessment(a.getId(), json(payload),
                "productive:" + a.getId() + ":" + a.getGradingRevision(), "productive-v1", a.getUserId());
    }

    private void checkAudio(AssessmentAttempt a) {
        try {
            var metadata = storage.metadata(bucket, a.getAudioObjectKey());
            if (metadata.contentLength() != a.getAudioContentLength() || !"audio/wav".equals(metadata.contentType())) {
                throw new BadRequestException("RECORDING_INVALID",
                        "Uploaded recording does not match its declared type and size");
            }
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ConflictException("RECORDING_MISSING", "Upload your recording before submitting");
            }
            throw e;
        }
    }

    private AssessmentTask publishedTask(UUID id) {
        return taskRepo.findById(id).filter(t -> t.getStatus() == AssessmentTaskStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Published task not found"));
    }

    private AssessmentAttempt owned(UUID id, UUID user, boolean lock) {
        return (lock ? attemptRepo.lockById(id) : attemptRepo.findById(id)).filter(a -> a.getUserId().equals(user))
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Submission not found"));
    }

    private AssessmentAttemptResult result(AssessmentAttempt a, boolean backOffice) {
        return resultMapper.from(a, backOffice);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize assessment");
        }
    }
}
