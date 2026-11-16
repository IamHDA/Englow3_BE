package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.dto.result.BlogImageResult;
import com.englow3.blog.entity.BlogPostImage;
import com.englow3.blog.repository.BlogPostImageRepository;
import com.englow3.shared.storage.PresignedUrlResolver;

class BlogImageLinksTest {

    private final BlogPostImageRepository imageRepo = mock(BlogPostImageRepository.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);
    private final BlogImageLinks links = new BlogImageLinks(imageRepo, presignedUrls);

    private final UUID postId = UUID.randomUUID();
    private final UUID uploader = UUID.randomUUID();

    BlogImageLinksTest() {
        ReflectionTestUtils.setField(links, "blogBucket", "blog");
        ReflectionTestUtils.setField(links, "imageUrlTtl", Duration.ofHours(3));
        when(presignedUrls.resolve(anyString(), anyString(), any()))
                .thenAnswer(invocation -> "https://storage.example/get/" + invocation.getArgument(1));
    }

    private BlogPostImage image() {
        UUID id = UUID.randomUUID();
        return BlogPostImage.issue(id, uploader, "posts/%s/%s.png".formatted(uploader, id), "image/png", 1_000,
                Instant.parse("2026-11-16T10:00:00Z"));
    }

    @Test
    void eachImageGetsALinkSignedForTheBlogBucketAndTheConfiguredLifetime() {
        BlogPostImage only = image();
        when(imageRepo.findByPostId(postId)).thenReturn(List.of(only));

        List<BlogImageResult> result = links.of(postId, "![x](blog-image:" + only.getId() + ")");

        assertThat(result).singleElement().satisfies(link -> {
            assertThat(link.id()).isEqualTo(only.getId());
            assertThat(link.url()).isEqualTo("https://storage.example/get/" + only.getObjectKey());
        });
        verify(presignedUrls).resolve("blog", only.getObjectKey(), Duration.ofHours(3));
    }

    @Test
    void theImagesComeInTheOrderTheTextFirstShowsThemWhateverOrderTheDatabaseGaveThem() {
        BlogPostImage first = image();
        BlogPostImage second = image();
        BlogPostImage third = image();
        when(imageRepo.findByPostId(postId)).thenReturn(List.of(third, first, second));
        String body = "![a](blog-image:%s) text ![b](blog-image:%s) ![c](blog-image:%s)".formatted(first.getId(),
                second.getId(), third.getId());

        assertThat(links.of(postId, body)).extracting(BlogImageResult::id).containsExactly(first.getId(),
                second.getId(), third.getId());
    }

    @Test
    void anImageTheTextDoesNotNameComesAfterTheOnesItDoes() {
        BlogPostImage named = image();
        BlogPostImage unnamed = image();
        when(imageRepo.findByPostId(postId)).thenReturn(List.of(unnamed, named));

        assertThat(links.of(postId, "![a](blog-image:%s)".formatted(named.getId()))).extracting(BlogImageResult::id)
                .containsExactly(named.getId(), unnamed.getId());
    }

    @Test
    void aPostWithNoImagesSignsNothing() {
        when(imageRepo.findByPostId(postId)).thenReturn(List.of());

        assertThat(links.of(postId, "just words")).isEmpty();

        verifyNoInteractions(presignedUrls);
    }
}
