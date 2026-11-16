package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.blog.dto.command.StartImageUploadCommand;
import com.englow3.blog.dto.result.BlogImageUploadTicket;
import com.englow3.blog.entity.BlogPostImage;
import com.englow3.blog.helper.BlogImages;
import com.englow3.blog.repository.BlogPostImageRepository;
import com.englow3.blog.service.BlogImageService;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hands out upload URLs for blog images and sweeps the ones no post took. The image row, written when the URL is
 * issued, is what lets the sweep find a file that never made it into a post: without it a person could put any number
 * of files in the bucket and never save a post.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogImageServiceImpl implements BlogImageService {

    /** Each start signs a URL for up to 5 MB, so a script must not be able to ask for them without end. */
    static final int MAX_STARTS_PER_WINDOW = 20;
    static final Duration START_WINDOW = Duration.ofMinutes(10);

    /** Long past the minutes an upload URL lasts and the time it takes to write a post, so nothing in use is swept. */
    static final Duration UNATTACHED_IMAGE_AGE = Duration.ofHours(24);

    private final UserDirectory userDirectory;
    private final BlogPostImageRepository imageRepo;
    private final ObjectStorageClient objectStorage;
    private final UserRateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Value("${app.storage.blog-bucket}")
    private String blogBucket;

    @Value("${app.storage.blog-upload-url-ttl:PT10M}")
    private Duration uploadUrlTtl;

    @Override
    @Transactional
    public BlogImageUploadTicket startUpload(StartImageUploadCommand command) {
        BlogImages.requireAccepted(command.contentType(), command.contentLength());

        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-image-upload", MAX_STARTS_PER_WINDOW, START_WINDOW);

        UUID imageId = UUID.randomUUID();
        String objectKey = BlogImages.objectKey(me, imageId, command.contentType());
        // Accepted above, so at most 5 MB: it fits an int.
        imageRepo.save(BlogPostImage.issue(imageId, me, objectKey, command.contentType(), (int) command.contentLength(),
                Instant.now(clock)));
        // Signing is arithmetic on the credentials, not a request to the store, so it is safe inside the transaction.
        String uploadUrl = objectStorage
                .presignPut(blogBucket, objectKey, command.contentType(), command.contentLength(), uploadUrlTtl)
                .toString();

        return new BlogImageUploadTicket(imageId, uploadUrl, command.contentType(), uploadUrlTtl.getSeconds(),
                "blog-image:" + imageId);
    }

    /**
     * An image still in no post a day after its URL was issued will never be: the URL lasted minutes, and the editor
     * saves the post it belongs to soon after. Each row is deleted in its own short transaction, and only whoever
     * deletes it removes the object, so two instances sweeping at once cannot both claim one, and neither can race a
     * post that is taking it. The object is deleted after the row is gone, outside any transaction.
     */
    @Override
    public int removeUnattached() {
        Instant cutoff = Instant.now(clock).minus(UNATTACHED_IMAGE_AGE);
        int removed = 0;
        while (true) {
            List<BlogPostImage> stale = imageRepo.findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(cutoff);
            int removedInBatch = 0;
            for (BlogPostImage image : stale) {
                Integer deleted = transactions.execute(tx -> imageRepo.deleteIfUnattached(image.getId()));
                if (deleted == null || deleted == 0) {
                    continue;
                }
                removedInBatch++;
                try {
                    objectStorage.delete(blogBucket, image.getObjectKey());
                } catch (RuntimeException failure) {
                    // The row is what anyone reads; a file left behind is a stray in the bucket, not a broken post.
                    log.warn("Could not remove the object of unattached blog image {}", image.getId(), failure);
                }
            }
            removed += removedInBatch;
            if (removedInBatch == 0) {
                return removed;
            }
        }
    }
}
