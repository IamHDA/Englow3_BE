package com.englow3.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;

class ChatMessageTest {

    private static final Instant SENT = Instant.parse("2026-11-15T08:00:00Z");
    private static final Instant RECALLED = Instant.parse("2026-11-15T08:05:00Z");
    private static final String IMAGE_KEY = "conversations/c/u.png";

    private final UUID conversationId = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private ChatMessage message(String body, String imageKey) {
        return ChatMessage.send(conversationId, 4, sender, "client-1", body, imageKey, SENT);
    }

    @Nested
    class Sending {

        @Test
        void textAloneIsAMessage() {
            ChatMessage message = message("hello", null);

            assertThat(message.getBody()).isEqualTo("hello");
            assertThat(message.getImageObjectKey()).isNull();
            assertThat(message.isRecalled()).isFalse();
            assertThat(message.getId()).isNotNull();
            assertThat(message.getConversationId()).isEqualTo(conversationId);
            assertThat(message.getSeq()).isEqualTo(4);
            assertThat(message.getSenderId()).isEqualTo(sender);
            assertThat(message.getClientId()).isEqualTo("client-1");
            assertThat(message.getSentAt()).isEqualTo(SENT);
            assertThat(message.getRecalledAt()).isNull();
        }

        @Test
        void anImageAloneIsAMessage() {
            ChatMessage message = message(null, IMAGE_KEY);

            assertThat(message.getBody()).isNull();
            assertThat(message.getImageObjectKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        void textAndAnImageTogetherAreAMessage() {
            ChatMessage message = message("look", IMAGE_KEY);

            assertThat(message.getBody()).isEqualTo("look");
            assertThat(message.getImageObjectKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        void textThatHasWordsIsKeptExactlyAsTyped() {
            ChatMessage message = message("  two  words\nand a new line \n", null);

            assertThat(message.getBody()).isEqualTo("  two  words\nand a new line \n");
        }

        @Test
        void textOfOnlyWhitespaceIsNoTextAndWithNoImageIsEmpty() {
            assertThatThrownBy(() -> message(" \n\t ", null)).isInstanceOfSatisfying(BadRequestException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_EMPTY"));
        }

        @Test
        void whitespaceBesideAnImageLeavesTheImageAlone() {
            ChatMessage message = message("   ", IMAGE_KEY);

            assertThat(message.getBody()).isNull();
            assertThat(message.getImageObjectKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        void noTextAndNoImageIsEmpty() {
            assertThatThrownBy(() -> message(null, null)).isInstanceOfSatisfying(BadRequestException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_EMPTY"));
        }

        @Test
        void fourThousandCharactersAreAllowed() {
            assertThat(message("a".repeat(4000), null).getBody()).hasSize(4000);
        }

        @Test
        void fourThousandAndOneCharactersAreTooLong() {
            assertThatThrownBy(() -> message("a".repeat(4001), null)).isInstanceOfSatisfying(BadRequestException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_TOO_LONG"));
        }
    }

    @Nested
    class Recalling {

        @Test
        void theSenderTakesBackTheTextAndGetsTheImageKeyToDelete() {
            ChatMessage message = message("oops", IMAGE_KEY);

            String removed = message.recall(sender, RECALLED);

            assertThat(removed).isEqualTo(IMAGE_KEY);
            assertThat(message.getBody()).isNull();
            assertThat(message.getImageObjectKey()).isNull();
            assertThat(message.getRecalledAt()).isEqualTo(RECALLED);
            assertThat(message.isRecalled()).isTrue();
        }

        @Test
        void aMessageWithNoImageHasNoKeyToDelete() {
            assertThat(message("oops", null).recall(sender, RECALLED)).isNull();
        }

        @Test
        void anotherPersonCannotRecallIt() {
            ChatMessage message = message("mine", null);

            assertThatThrownBy(() -> message.recall(other, RECALLED)).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_SENDER"));
            assertThat(message.getBody()).isEqualTo("mine");
            assertThat(message.isRecalled()).isFalse();
        }

        @Test
        void aMessageWhoseSenderErasedTheirAccountCannotBeRecalledByAnyone() {
            ChatMessage message = ChatMessage.send(conversationId, 1, null, "client-2", "from a ghost", null, SENT);

            assertThatThrownBy(() -> message.recall(other, RECALLED)).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_SENDER"));
        }

        @Test
        void aSecondRecallIsRefused() {
            ChatMessage message = message("oops", IMAGE_KEY);
            message.recall(sender, RECALLED);

            assertThatThrownBy(() -> message.recall(sender, RECALLED.plusSeconds(5))).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_ALREADY_RECALLED"));
            assertThat(message.getRecalledAt()).isEqualTo(RECALLED);
        }
    }

    @Nested
    class Preview {

        @Test
        void shortTextIsItsOwnPreview() {
            assertThat(message("hello", null).preview()).isEqualTo("hello");
        }

        @Test
        void longTextIsCutAtOneHundredAndTwentyCharacters() {
            assertThat(message("a".repeat(500), null).preview()).hasSize(120);
        }

        @Test
        void aCharacterOutsideTheBasicPlaneIsNeverCutInHalf() {
            String emoji = "😀";

            String preview = message(emoji.repeat(200), null).preview();

            assertThat(preview).isEqualTo(emoji.repeat(120));
        }

        @Test
        void thereIsNoPreviewForAnImageAloneOrARecalledMessage() {
            assertThat(message(null, IMAGE_KEY).preview()).isNull();
            ChatMessage recalled = message("oops", null);
            recalled.recall(sender, RECALLED);
            assertThat(recalled.preview()).isNull();
        }
    }
}
