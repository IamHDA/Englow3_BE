package com.englow3.learning.dto.response;

import java.time.Duration;

import com.englow3.shared.storage.ObjectStorageClient;

/**
 * Signs flashcard audio for one request, the same way {@code ExamMediaUrls} signs exam media. Pronunciation clips are
 * not secret the way a listening paper is, but they live in the same private bucket arrangement, and a signed link that
 * expires is one less thing to reason about than a permanently public one.
 */
public record FlashcardMediaUrls(ObjectStorageClient objectStorage, String bucket, Duration ttl) {

    public String urlFor(String objectKey) {
        return objectKey == null ? null : objectStorage.presignGet(bucket, objectKey, ttl).toString();
    }
}
