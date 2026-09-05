package com.englow3.ai.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TutorDomainTest {
    @Nested
    class Success {

        @Test
        void archivesAConversation() {
            TutorConversation conversation = TutorConversation.start(UUID.randomUUID(), "Grammar practice");

            conversation.archive();

            assertThat(conversation.active()).isFalse();
            assertThat(conversation.getStatus()).isEqualTo(TutorConversationStatus.ARCHIVED);
        }

        @Test
        void completesAPendingAssistantMessage() {
            TutorMessage message = TutorMessage.pendingAssistant(UUID.randomUUID(), UUID.randomUUID(),
                    TutorMode.Q_AND_A, true);

            message.complete("Use the present perfect here.", "test-model", 20, 10);

            assertThat(message.getStatus()).isEqualTo(TutorMessageStatus.COMPLETED);
            assertThat(message.getContent()).contains("present perfect");
            assertThat(message.getInputTokens()).isEqualTo(20);
        }

    }

}
