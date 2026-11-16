package com.englow3.chat.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sweeps images nobody sent and conversations nobody is left in. Safe on several instances at once: every removal is a
 * conditional delete, and only the instance whose delete took effect removes the object.
 */
@Component
@ConditionalOnProperty(name = "app.chat.cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ChatCleanup {

    private final ChatImageService imageService;
    private final ChatConversationService conversationService;

    @Scheduled(fixedDelayString = "${app.chat.cleanup-delay:1h}", initialDelayString = "${app.chat.cleanup-delay:1h}")
    public void sweep() {
        int uploads = imageService.removeAbandonedUploads();
        if (uploads > 0) {
            log.info("Removed {} chat image uploads nobody sent", uploads);
        }
        int conversations = conversationService.removeEmptyConversations();
        if (conversations > 0) {
            log.info("Removed {} chat conversations nobody is left in", conversations);
        }
    }
}
