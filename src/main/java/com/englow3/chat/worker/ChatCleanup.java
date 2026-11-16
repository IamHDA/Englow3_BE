package com.englow3.chat.worker;

import java.util.function.IntSupplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;
import com.englow3.chat.service.ChatSocketTicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sweeps images nobody sent, conversations nobody is left in and tickets that expired long ago. Safe on several
 * instances at once: every removal is a conditional delete, and only the instance whose delete took effect removes the
 * object.
 */
@Component
@ConditionalOnProperty(name = "app.chat.cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ChatCleanup {

    private final ChatImageService imageService;
    private final ChatConversationService conversationService;
    private final ChatSocketTicketService ticketService;

    @Scheduled(fixedDelayString = "${app.chat.cleanup-delay:1h}", initialDelayString = "${app.chat.cleanup-delay:1h}")
    public void sweep() {
        run("chat image uploads nobody sent", imageService::removeAbandonedUploads);
        run("chat conversations nobody is left in", conversationService::removeEmptyConversations);
        run("expired chat socket tickets", ticketService::removeExpired);
    }

    /**
     * Each sweep stands alone: one that fails (the store is down, a row is locked) is logged and left to the next
     * cycle, and does not stop the sweeps after it.
     */
    private void run(String what, IntSupplier sweep) {
        try {
            int removed = sweep.getAsInt();
            if (removed > 0) {
                log.info("Removed {} {}", removed, what);
            }
        } catch (RuntimeException failure) {
            log.warn("Sweep of {} failed; the next cycle will try again", what, failure);
        }
    }
}
