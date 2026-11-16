package com.englow3.blog.helper;

import java.util.Set;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

/**
 * What a blog image may be and where it lives in object storage.
 * <p>
 * The key convention belongs to this module, not to {@code shared/storage}: the storage client moves bytes and has no
 * opinion about what a key means. Pure, so both halves can be checked without a bucket. Chat has its own copy of the
 * same idea; two modules' rules for their own images are not yet one rule.
 */
public final class BlogImages {

    /**
     * Checked before a URL is issued and bound into the signature after: a presigned PUT never passes through this
     * application, so these are the only moments a size can be refused.
     */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private BlogImages() {
    }

    public static void requireAccepted(String contentType, long contentLength) {
        if (contentType == null || !TYPES.contains(contentType)) {
            throw new BadRequestException("BLOG_IMAGE_TYPE_NOT_ALLOWED",
                    "Images must be JPEG, PNG or WebP; %s is not accepted".formatted(contentType));
        }
        if (contentLength <= 0 || contentLength > MAX_BYTES) {
            throw new BadRequestException("BLOG_IMAGE_TOO_LARGE",
                    "An image must be between 1 byte and %d MB".formatted(MAX_BYTES / (1024 * 1024)));
        }
    }

    /**
     * One object per upload, under its uploader's folder. The image id makes the key unique without a counter, and the
     * folder is the uploader's rather than the post's because the post does not exist yet when the key is issued.
     */
    public static String objectKey(UUID uploaderId, UUID imageId, String contentType) {
        return "posts/%s/%s.%s".formatted(uploaderId, imageId, extensionFor(contentType));
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Not an accepted blog image type: " + contentType);
        };
    }
}
