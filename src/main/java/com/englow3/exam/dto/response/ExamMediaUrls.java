package com.englow3.exam.dto.response;

import java.time.Duration;

import com.englow3.shared.storage.ObjectStorageClient;

/**
 * Turns a stored object key into a link the admin screen can actually play. Exam media lives in the private bucket and
 * is signed per request rather than served from a public URL like an avatar: a listening recording is the paper, and a
 * stable public link to it is a leaked paper. Built once per request and handed down the response tree, because passing
 * the client, the bucket and the ttl separately through five nesting levels would be noise. Signing is local crypto, no
 * network call, so one signature per media key on the page costs nothing worth measuring.
 */
public record ExamMediaUrls(ObjectStorageClient objectStorage, String bucket, Duration ttl) {

    public static ExamMediaUrls of(ObjectStorageClient objectStorage, Duration ttl) {
        return new ExamMediaUrls(objectStorage, objectStorage.defaultBucket(), ttl);
    }

    public String urlFor(String objectKey) {
        return objectKey == null ? null : objectStorage.presignGet(bucket, objectKey, ttl).toString();
    }
}
