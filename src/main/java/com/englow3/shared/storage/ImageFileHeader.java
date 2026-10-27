package com.englow3.shared.storage;

import java.nio.charset.StandardCharsets;

/**
 * Small format check before an image upload is forwarded to storage. The declared type is only the client's word, and
 * the bucket serves a file under whatever type it was stored with - so without this any bytes at all could be published
 * under an image's name.
 */
public final class ImageFileHeader {

    /** Enough of the file to tell every accepted format apart. */
    public static final int LENGTH = 12;

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

    private ImageFileHeader() {
    }

    public static boolean matches(byte[] header, String type) {
        if ("image/png".equals(type)) {
            return startsWith(header, PNG);
        }
        if ("image/jpeg".equals(type)) {
            return header.length >= 3 && (header[0] & 255) == 0xFF && (header[1] & 255) == 0xD8
                    && (header[2] & 255) == 0xFF;
        }
        if ("image/webp".equals(type)) {
            return header.length >= LENGTH && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
        }
        return false;
    }

    private static boolean startsWith(byte[] header, byte[] prefix) {
        if (header.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (header[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
