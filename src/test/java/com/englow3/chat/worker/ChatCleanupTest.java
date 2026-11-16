package com.englow3.chat.worker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;
import com.englow3.chat.service.ChatSocketTicketService;

/** Each sweep stands alone: one that fails must not take the ones after it down with it. */
class ChatCleanupTest {

    private final ChatImageService imageService = mock(ChatImageService.class);
    private final ChatConversationService conversationService = mock(ChatConversationService.class);
    private final ChatSocketTicketService ticketService = mock(ChatSocketTicketService.class);
    private final ChatCleanup cleanup = new ChatCleanup(imageService, conversationService, ticketService);

    @Test
    void runsEverySweep() {
        when(imageService.removeAbandonedUploads()).thenReturn(2);
        when(conversationService.removeEmptyConversations()).thenReturn(1);
        when(ticketService.removeExpired()).thenReturn(5);

        cleanup.sweep();

        verify(imageService).removeAbandonedUploads();
        verify(conversationService).removeEmptyConversations();
        verify(ticketService).removeExpired();
    }

    @Test
    void aFailingUploadSweepDoesNotSkipTheOthers() {
        when(imageService.removeAbandonedUploads()).thenThrow(new IllegalStateException("store down"));

        cleanup.sweep();

        verify(conversationService).removeEmptyConversations();
        verify(ticketService).removeExpired();
    }

    @Test
    void aFailingConversationSweepDoesNotSkipTheTicketSweepNorUndoTheUploadSweep() {
        when(conversationService.removeEmptyConversations()).thenThrow(new IllegalStateException("row locked"));

        cleanup.sweep();

        verify(imageService).removeAbandonedUploads();
        verify(ticketService).removeExpired();
    }

    @Test
    void aFailingTicketSweepIsLoggedNotThrown() {
        when(ticketService.removeExpired()).thenThrow(new IllegalStateException("db down"));

        cleanup.sweep();

        verify(imageService).removeAbandonedUploads();
        verify(conversationService).removeEmptyConversations();
    }
}
