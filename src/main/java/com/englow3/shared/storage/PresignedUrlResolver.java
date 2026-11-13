package com.englow3.shared.storage;

import java.time.Duration;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PresignedUrlResolver {

    private final ObjectStorageClient objectStorageClient;

    public String resolve(String bucket, String objectKey, Duration ttl) {
        if (objectKey == null) {
            return null;
        }

        return objectStorageClient.presignGet(bucket, objectKey, ttl).toString();
    }
}
