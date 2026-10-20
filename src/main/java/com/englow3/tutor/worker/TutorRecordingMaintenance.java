package com.englow3.tutor.worker;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.tutor.repository.TutorRecordingRepository;
import com.englow3.tutor.service.TutorAudioService;
import com.englow3.tutor.service.TutorTranscriptionWriter;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "app.tutor.maintenance-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TutorRecordingMaintenance {
    private final TutorAudioService audio;
    private final TutorRecordingRepository recordingRepo;
    private final AiJobQueue jobs;
    private final TutorTranscriptionWriter writer;
    private final com.englow3.tutor.repository.TutorDataRepository dataRepo;

    @Scheduled(fixedDelayString = "${app.ai.worker.reconcile-delay:30s}")
    public void reconcile() {
        recordingRepo.findByStatusOrderByCreatedAtAsc("QUEUED", PageRequest.of(0, 50)).forEach(row -> {
            if (jobs.hasFailedTutorTranscription(row.getId()))
                writer.failed(row.getId(), "TUTOR_TRANSCRIPTION_FAILED");
        });
    }

    @Scheduled(fixedDelayString = "${app.tutor.audio-cleanup-delay:5m}")
    public void cleanup() {
        audio.cleanup();
    }

    @Scheduled(fixedDelayString = "1h")
    public void expireDeliveryIds() {
        dataRepo.expireDeliveryTombstones();
    }
}
