package com.englow3.blog.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.englow3.blog.service.BlogImageService;

class BlogImageCleanupTest {

    private final BlogImageService imageService = mock(BlogImageService.class);
    private final BlogImageCleanup cleanup = new BlogImageCleanup(imageService);

    @Test
    void sweepsTheImagesNoPostTook() {
        when(imageService.removeUnattached()).thenReturn(3);

        cleanup.sweep();

        verify(imageService).removeUnattached();
    }

    @Test
    void aSweepThatFailsIsLoggedNotThrownSoTheNextCycleStillRuns() {
        when(imageService.removeUnattached()).thenThrow(new IllegalStateException("store down"));

        assertThatCode(cleanup::sweep).doesNotThrowAnyException();
    }
}
