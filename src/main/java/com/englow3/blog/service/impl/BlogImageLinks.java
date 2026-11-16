package com.englow3.blog.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.englow3.blog.dto.result.BlogImageResult;
import com.englow3.blog.helper.BlogMarkdown;
import com.englow3.blog.repository.BlogPostImageRepository;
import com.englow3.shared.storage.PresignedUrlResolver;

import lombok.RequiredArgsConstructor;

/**
 * The images of a post with a short-lived link to read each, for everything that shows a post in full - the reader's
 * page and the moderator's. The bucket is private and the text names an image by id only, so a link is signed here
 * (signing asks no one, so it may happen inside a transaction) each time a post is shown and never stored.
 * Package-private: it belongs to the services of this module.
 */
@Component
@RequiredArgsConstructor
class BlogImageLinks {

    private final BlogPostImageRepository imageRepo;
    private final PresignedUrlResolver presignedUrls;

    @Value("${app.storage.blog-bucket}")
    private String blogBucket;

    @Value("${app.storage.blog-image-url-ttl:PT3H}")
    private Duration imageUrlTtl;

    /** The post's images in the order the text first shows them; one the text does not name comes last. */
    List<BlogImageResult> of(UUID postId, String body) {
        List<UUID> order = BlogMarkdown.imageReferences(body);
        return imageRepo.findByPostId(postId).stream()
                .sorted((a, b) -> Integer.compare(position(order, a.getId()), position(order, b.getId())))
                .map(image -> new BlogImageResult(image.getId(),
                        presignedUrls.resolve(blogBucket, image.getObjectKey(), imageUrlTtl)))
                .toList();
    }

    private static int position(List<UUID> order, UUID id) {
        int index = order.indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
