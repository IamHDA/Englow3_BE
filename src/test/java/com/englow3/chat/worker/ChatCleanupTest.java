package com.englow3.chat.worker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;

/** Each sweep stands alone: one that fails must not take the ones after it down with it. */
class ChatCleanupTest {

    private final ChatImageService imageService = mock(ChatImageService.class);
    private final ChatConversationService conversationService = mock(ChatConversationService.class);
    private final ChatCleanup cleanup = new ChatCleanup(imageService, conversationService);

    @Test
    void runsEverySweep() {
        when(imageService.removeAbandonedUploads()).thenReturn(2);
        when(conversationService.removeEmptyConversations()).thenReturn(1);

        cleanup.sweep();

        verify(imageService).removeAbandonedUploads();
        verify(conversationService).removeEmptyConversations();
    }

    @Test
    void aFailingUploadSweepDoesNotSkipTheConversationSweep() {
        when(imageService.removeAbandonedUploads()).thenThrow(new IllegalStateException("store down"));

        cleanup.sweep();

        verify(conversationService).removeEmptyConversations();
    }

    @Test
    void aFailingConversationSweepIsLoggedNotThrown() {
        when(conversationService.removeEmptyConversations()).thenThrow(new IllegalStateException("row locked"));

        cleanup.sweep();

        verify(imageService).removeAbandonedUploads();
    }
}
