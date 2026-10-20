package com.englow3.tutor.service.impl;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.*;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.tutor.dto.result.TutorRecordingResult;
import com.englow3.tutor.entity.TutorRecording;
import com.englow3.tutor.helper.*;
import com.englow3.tutor.repository.TutorRecordingRepository;
import com.englow3.tutor.service.TutorAudioService;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorAudioServiceImpl implements TutorAudioService {
    private final TutorRecordingRepository recordingRepo;
    private final UserDirectory users;
    private final AiJobQueue jobs;
    private final ObjectStorageClient storage;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final com.englow3.tutor.service.TutorPolicyService policy;
    private final Semaphore uploads = new Semaphore(8);
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    @Value("${app.tutor.voice-enabled:false}")
    private boolean enabled;
    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;
    @Value("${app.tutor.audio-bucket:speaking}")
    private String bucket;

    private record Reserved(TutorRecording recording, boolean accepted) {
    }

    public UploadPermit reserveUpload() {
        if (!enabled || !aiEnabled || !policy.accepting())
            throw new ConflictException("TUTOR_VOICE_DISABLED", "Voice input is unavailable");
        UUID user = users.requireCurrentUserId();
        if (!active.add(user))
            throw new ConflictException("TUTOR_AUDIO_BUSY", "Your recording is already uploading");
        if (!uploads.tryAcquire()) {
            active.remove(user);
            throw new ConflictException("TUTOR_AUDIO_BUSY", "Voice input is busy; retry shortly");
        }
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                active.remove(user);
                uploads.release();
            }
        };
    }

    public TutorRecordingResult upload(UUID clientId, String locale, byte[] wav) {
        if (!enabled || !aiEnabled || !policy.accepting())
            throw new ConflictException("TUTOR_VOICE_DISABLED", "Voice input is unavailable");
        if (!Set.of("en-US", "vi-VN").contains(locale))
            throw new BadRequestException("TUTOR_AUDIO_LOCALE", "Choose English or Vietnamese");
        TutorWav.validate(wav);
        UUID user = users.requireCurrentUserId();
        String fingerprint = TutorDelivery.fingerprint(locale, wav);
        Reserved reserved = transactions.execute(tx -> {
            jobs.lockBudget(user);
            TutorRecording prior = recordingRepo.findByUserIdAndClientMessageId(user, clientId).orElse(null);
            if (prior != null) {
                if (!prior.getFingerprint().equals(fingerprint))
                    throw new ConflictException("TUTOR_REQUEST_CONFLICT", "This recording ID belongs to another file");
                return new Reserved(prior, false);
            }
            if (!jobs.hasDailyAllowance(user))
                throw new ConflictException("TUTOR_DAILY_LIMIT_REACHED",
                        "Today's shared AI allowance has been reached");
            return new Reserved(recordingRepo
                    .saveAndFlush(TutorRecording.receiving(user, clientId, fingerprint, wav.length, locale)), true);
        });
        TutorRecording recording = reserved.recording();
        if (!reserved.accepted())
            return TutorRecordingResult.of(recording);
        try {
            storage.upload(bucket, recording.getObjectKey(), new ByteArrayInputStream(wav), wav.length, "audio/wav");
            return transactions.execute(tx -> {
                jobs.lockBudget(user);
                TutorRecording locked = recordingRepo.lockById(recording.getId()).orElse(null);
                if (locked == null || "DELETED".equals(locked.getStatus()))
                    return new TutorRecordingResult(recording.getId(), "DELETED", null, null);
                try {
                    String payload = mapper.writeValueAsString(Map.of("recordingId", locked.getId(), "objectKey",
                            locked.getObjectKey(), "locale", locale));
                    jobs.enqueueTutorTranscription(locked.getId(), payload, "TUTOR_AUDIO:" + locked.getId(), user);
                } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
                    throw new IllegalStateException(impossible);
                }
                locked.queued();
                return TutorRecordingResult.of(locked);
            });
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(tx -> recordingRepo.lockById(recording.getId())
                    .ifPresent(row -> row.failed("TUTOR_AUDIO_UPLOAD_FAILED")));
            throw failure;
        } finally {
            boolean keep = recordingRepo.findById(recording.getId())
                    .map(row -> "QUEUED".equals(row.getStatus()) || "READY".equals(row.getStatus())).orElse(false);
            if (!keep)
                try {
                    storage.delete(bucket, recording.getObjectKey());
                } catch (RuntimeException ignored) {
                    /* retained row is a cleanup intent */}
        }
    }

    public TutorRecordingResult receipt(UUID clientId) {
        return TutorRecordingResult
                .of(recordingRepo.findByUserIdAndClientMessageId(users.requireCurrentUserId(), clientId)
                        .orElseThrow(() -> new NotFoundException("TUTOR_RECORDING_NOT_FOUND", "Recording not found")));
    }

    public void discard(UUID id) {
        UUID user = users.requireCurrentUserId();
        TutorRecording row = transactions.execute(tx -> {
            TutorRecording recording = recordingRepo.lockById(id).filter(r -> r.getUserId().equals(user))
                    .orElseThrow(() -> new NotFoundException("TUTOR_RECORDING_NOT_FOUND", "Recording not found"));
            recording.discard();
            return recording;
        });
        remove(row);
    }

    private void remove(TutorRecording row) {
        jobs.purgeTutorRecording(row.getId(), row.getUserId());
        storage.delete(bucket, row.getObjectKey());
        // Keep the tombstone briefly so an upload still finishing cannot enqueue a cancelled recording.
        if (row.getCreatedAt().isBefore(clock.instant().minusSeconds(300)))
            transactions.executeWithoutResult(tx -> {
                recordingRepo.lockById(row.getId()).filter(r -> "DELETED".equals(r.getStatus()))
                        .ifPresent(recordingRepo::delete);
            });
    }

    public void cleanup() {
        for (TutorRecording row : recordingRepo.cleanupCandidates(clock.instant().minusSeconds(86400),
                clock.instant().minusSeconds(300), PageRequest.of(0, 50))) {
            try {
                transactions.executeWithoutResult(
                        tx -> recordingRepo.lockById(row.getId()).ifPresent(TutorRecording::discard));
                remove(row);
            } catch (RuntimeException ignored) {
                /* retry a durable cleanup intent next pass */}
        }
    }
}
