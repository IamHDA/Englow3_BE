package com.englow3.blog.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.blog.service.BlogImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sweeps images that were uploaded and never put in a saved post. Safe on several instances at once: every removal is a
 * conditional delete, and only the instance whose delete took effect removes the object.
 */
@Component
@ConditionalOnProperty(name = "app.blog.cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class BlogImageCleanup {

    private final BlogImageService imageService;

    @Scheduled(fixedDelayString = "${app.blog.cleanup-delay:1h}", initialDelayString = "${app.blog.cleanup-delay:1h}")
    public void sweep() {
        try {
            int removed = imageService.removeUnattached();
            if (removed > 0) {
                log.info("Removed {} blog images no post took", removed);
            }
        } catch (RuntimeException failure) {
            // The store is down, a row is locked: the next cycle tries again.
            log.warn("Sweep of unattached blog images failed; the next cycle will try again", failure);
        }
    }
}
