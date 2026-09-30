package com.englow3.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class PresignedUrlResolverTest {

    private final ObjectStorageClient objectStorageClient = mock(ObjectStorageClient.class);
    private final PresignedUrlResolver resolver = new PresignedUrlResolver(objectStorageClient);

    @Test
    void returnsNullWithoutCallingStorageForNullKey() {
        assertThat(resolver.resolve("bucket", null, Duration.ofHours(1))).isNull();

        verify(objectStorageClient, never()).presignGet("bucket", null, Duration.ofHours(1));
    }

    @Test
    void delegatesBucketKeyAndTtl() throws MalformedURLException {
        Duration ttl = Duration.ofHours(3);
        URL url = new URL("https://example.test/audio");
        when(objectStorageClient.presignGet("learning", "audio/key.mp3", ttl)).thenReturn(url);

        assertThat(resolver.resolve("learning", "audio/key.mp3", ttl)).isEqualTo(url.toString());

        verify(objectStorageClient).presignGet("learning", "audio/key.mp3", ttl);
    }
}
