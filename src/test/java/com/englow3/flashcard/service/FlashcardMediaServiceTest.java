package com.englow3.flashcard.service;

import com.englow3.flashcard.service.impl.FlashcardMediaServiceImpl;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlashcardMediaServiceTest {
    private final ObjectStorageClient storage = mock(ObjectStorageClient.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final FlashcardMediaService service = new FlashcardMediaServiceImpl(storage, users, "learning");

    @Test
    void validatesAndStoresAudioUnderTheAuthenticatedAuthor() throws Exception {
        UUID author = UUID.randomUUID();
        when(users.requireCurrentUserId()).thenReturn(author);
        when(storage.presignGet(eq("learning"), anyString(), eq(Duration.ofHours(1))))
                .thenReturn(URI.create("http://localhost/audio.wav").toURL());
        byte[] bytes = "RIFF1234WAVEdata".getBytes(StandardCharsets.US_ASCII);
        var result = service.upload(new ByteArrayInputStream(bytes), bytes.length, "audio/wav");
        assertThat(result.objectKey()).startsWith("flashcards/authoring/" + author + "/").endsWith(".wav");
        verify(storage).upload(eq("learning"), eq(result.objectKey()), any(InputStream.class), eq((long) bytes.length),
                eq("audio/wav"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "audio/wav", "audio/mpeg", "image/png" })
    void rejectsFilesWhoseHeaderDoesNotMatchAudioBeforeStorage(String type) {
        assertThatThrownBy(() -> service
                .upload(new ByteArrayInputStream("not an audio file".getBytes(StandardCharsets.US_ASCII)), 17, type))
                        .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void refusesAnOversizeFileBeforeReadingIt() {
        InputStream input = mock(InputStream.class);
        assertThatThrownBy(() -> service.upload(input, 12L * 1024 * 1024 + 1, "audio/wav"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage, input);
    }
}
