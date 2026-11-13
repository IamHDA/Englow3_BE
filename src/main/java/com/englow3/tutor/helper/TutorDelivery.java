package com.englow3.tutor.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Request fingerprints use length framing so delimiters in learner text cannot collide. */
public final class TutorDelivery {
    private TutorDelivery() {
    }

    public static String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update((byte) (value == null ? 0 : value instanceof byte[] ? 2 : 1));
                byte[] bytes = value instanceof byte[] binary ? binary
                        : (value == null ? "" : value.toString()).getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String jobKey(UUID messageId, int revision) {
        return "TUTOR_MESSAGE:" + messageId + (revision == 0 ? "" : ":" + revision);
    }
}
