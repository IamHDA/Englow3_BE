package com.englow3.speaking.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.speaking.service.SpeakingAttemptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Sweeps speaking attempts whose recording never arrived, and the uploads nothing will ever submit. */
@Component
@ConditionalOnProperty(name = "app.speaking.upload-cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SpeakingUploadCleanup {

    private final SpeakingAttemptService attempts;

    @Scheduled(fixedDelayString = "${app.speaking.upload-cleanup-delay:1h}", initialDelayString = "${app.speaking.upload-cleanup-delay:1h}")
    public void sweep() {
        int removed = attempts.removeAbandonedUploads();
        if (removed > 0) {
            log.info("Removed {} speaking attempts whose recording never arrived", removed);
        }
    }
}
