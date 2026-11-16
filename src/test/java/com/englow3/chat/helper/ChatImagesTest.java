package com.englow3.chat.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class ChatImagesTest {

    @Test
    void jpegPngAndWebpAreAccepted() {
        assertThatCode(() -> ChatImages.requireAccepted("image/jpeg", 1_000)).doesNotThrowAnyException();
        assertThatCode(() -> ChatImages.requireAccepted("image/png", 1_000)).doesNotThrowAnyException();
        assertThatCode(() -> ChatImages.requireAccepted("image/webp", 1_000)).doesNotThrowAnyException();
    }

    @Test
    void anyOtherTypeIsRefused() {
        for (String type : new String[] { "image/gif", "image/svg+xml", "application/pdf", "", null }) {
            assertThatThrownBy(() -> ChatImages.requireAccepted(type, 1_000)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_TYPE_NOT_ALLOWED"));
        }
    }

    @Test
    void oneByteAndExactlyFiveMegabytesAreAccepted() {
        assertThatCode(() -> ChatImages.requireAccepted("image/png", 1)).doesNotThrowAnyException();
        assertThatCode(() -> ChatImages.requireAccepted("image/png", 5L * 1024 * 1024)).doesNotThrowAnyException();
    }

    @Test
    void emptyAndOverFiveMegabytesAreTooLarge() {
        for (long length : new long[] { 0, -1, 5L * 1024 * 1024 + 1 }) {
            assertThatThrownBy(() -> ChatImages.requireAccepted("image/png", length)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_TOO_LARGE"));
        }
    }

    @Test
    void theKeyIsUnderTheConversationWithTheExtensionOfTheType() {
        UUID conversation = UUID.randomUUID();
        UUID upload = UUID.randomUUID();

        assertThat(ChatImages.objectKey(conversation, upload, "image/jpeg"))
                .isEqualTo("conversations/%s/%s.jpg".formatted(conversation, upload));
        assertThat(ChatImages.objectKey(conversation, upload, "image/png"))
                .isEqualTo("conversations/%s/%s.png".formatted(conversation, upload));
        assertThat(ChatImages.objectKey(conversation, upload, "image/webp"))
                .isEqualTo("conversations/%s/%s.webp".formatted(conversation, upload));
    }
}
