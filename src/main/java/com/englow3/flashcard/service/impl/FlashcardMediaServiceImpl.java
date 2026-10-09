package com.englow3.flashcard.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.englow3.flashcard.dto.result.FlashcardMediaResult;
import com.englow3.flashcard.service.FlashcardMediaService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.storage.AudioFileHeader;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;

@Service
public class FlashcardMediaServiceImpl implements FlashcardMediaService {
    private final ObjectStorageClient storage;
    private final UserDirectory users;
    private final String bucket;

    public FlashcardMediaServiceImpl(ObjectStorageClient storage, UserDirectory users,
            @Value("${app.storage.learning-bucket}") String bucket) {
        this.storage = storage;
        this.users = users;
        this.bucket = bucket;
    }

    public FlashcardMediaResult upload(InputStream content, long length, String type) {
        if (length < 12 || length > 12 * 1024 * 1024 || !("audio/wav".equals(type) || "audio/mpeg".equals(type))) {
            throw new BadRequestException("FLASHCARD_MEDIA_INVALID", "Choose a WAV or MP3 file of at most 12 MB");
        }
        try {
            var buffered = new java.io.BufferedInputStream(content);
            buffered.mark(12);
            byte[] header = buffered.readNBytes(12);
            buffered.reset();
            if (!AudioFileHeader.matches(header, type)) {
                throw new BadRequestException("FLASHCARD_MEDIA_INVALID", "The file does not match its audio format");
            }
            String key = "flashcards/authoring/" + users.requireCurrentUserId() + "/" + UUID.randomUUID()
                    + ("audio/wav".equals(type) ? ".wav" : ".mp3");
            storage.upload(bucket, key, buffered, length, type);
            return new FlashcardMediaResult(key, storage.presignGet(bucket, key, Duration.ofHours(1)).toString());
        } catch (IOException e) {
            throw new BadRequestException("FLASHCARD_MEDIA_UNREADABLE", "The audio file could not be read");
        }
    }
}
