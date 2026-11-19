package com.englow3.chat.helper;

import java.util.Set;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

/**
 * What a chat image may be and where it lives in object storage.
 * <p>
 * The key convention belongs to this module, not to {@code shared/storage}: the storage client moves bytes and has no
 * opinion about what a key means. Pure, so both halves can be checked without a bucket.
 */
public final class ChatImages {

    /**
     * Checked before a URL is issued and bound into the signature after: a presigned PUT never passes through this
     * application, so these are the only moments a size can be refused.
     */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private ChatImages() {
    }

    public static void requireAccepted(String contentType, long contentLength) {
        if (contentType == null || !TYPES.contains(contentType)) {
            throw new BadRequestException("CHAT_IMAGE_TYPE_NOT_ALLOWED",
                    "Images must be JPEG, PNG or WebP; %s is not accepted".formatted(contentType));
        }
        if (contentLength <= 0 || contentLength > MAX_BYTES) {
            throw new BadRequestException("CHAT_IMAGE_TOO_LARGE",
                    "An image must be between 1 byte and %d MB".formatted(MAX_BYTES / (1024 * 1024)));
        }
    }

    /**
     * One object per upload, under its conversation's folder, so a misconfigured bucket policy fails toward "one
     * conversation's images" rather than "everyone's". The upload id makes the key unique without a counter.
     */
    public static String objectKey(UUID conversationId, UUID uploadId, String contentType) {
        return "conversations/%s/%s.%s".formatted(conversationId, uploadId, extensionFor(contentType));
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Not an accepted chat image type: " + contentType);
        };
    }
}
