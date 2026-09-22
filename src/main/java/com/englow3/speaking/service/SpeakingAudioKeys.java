package com.englow3.speaking.service;

import java.util.Set;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;

/**
 * Where a recording lives in object storage, and what may be uploaded.
 * <p>
 * The key convention belongs to this module, not to {@code shared/storage}: the storage client moves bytes and has no
 * opinion about what a key means. Pure, so both halves can be checked without a bucket.
 */
public final class SpeakingAudioKeys {

    /**
     * What {@code ai_service} accepts. Checked here as well as there because the upload happens long before the
     * assessment does - refusing at the door beats handing back a presigned URL, letting the learner record, and only
     * then discovering the format was never going to work.
     */
    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of("audio/wav", "audio/x-wav", "audio/ogg");

    /**
     * The largest recording that will be accepted, checked before a URL is issued and bound into the signature after.
     * <p>
     * Ten megabytes is what {@code ai_service} reads before refusing, so anything larger could never be scored. At 16
     * kHz mono WAV that is roughly five minutes - far longer than any prompt, and short of making the bucket a cheap
     * place to park files.
     */
    static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private SpeakingAudioKeys() {
    }

    /**
     * One object per attempt, under the owner's folder.
     * <p>
     * The user id is in the path so a misconfigured bucket policy fails toward "one learner's recordings" rather than
     * "everyone's", and the attempt id makes the key unique without a counter - a retry of the same attempt overwrites
     * its own recording, which is what a retry means.
     */
    public static String audioKey(UUID userId, UUID attemptId, String contentType) {
        return "speaking/%s/%s%s".formatted(userId, attemptId, extensionFor(contentType));
    }

    public static void requireAcceptedContentType(String contentType) {
        if (contentType == null || !ACCEPTED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("SPEAKING_UNSUPPORTED_AUDIO_TYPE",
                    "Recordings must be WAV or OGG; %s is not accepted".formatted(contentType));
        }
    }

    /**
     * Refused here rather than at the bucket. A presigned PUT bypasses this application entirely, so the only moments a
     * size can be checked are before the URL exists and inside the signature - the request itself is never seen.
     */
    public static void requireAcceptedSize(long contentLength) {
        if (contentLength <= 0) {
            throw new BadRequestException("SPEAKING_EMPTY_RECORDING", "A recording cannot be empty");
        }
        if (contentLength > MAX_AUDIO_BYTES) {
            throw new BadRequestException("SPEAKING_RECORDING_TOO_LARGE",
                    "A recording may be at most %d MB".formatted(MAX_AUDIO_BYTES / (1024 * 1024)));
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "audio/ogg" -> ".ogg";
            case "audio/wav", "audio/x-wav" -> ".wav";
            // Unreachable while requireAcceptedContentType runs first, and a default beats a key with no extension.
            default -> ".bin";
        };
    }
}
