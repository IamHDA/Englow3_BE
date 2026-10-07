package com.englow3.shared.storage;

import java.nio.charset.StandardCharsets;

/** Small format check before forwarding an authoring upload to storage. */
public final class AudioFileHeader {
    private AudioFileHeader() {
    }

    public static boolean matches(byte[] header, String type) {
        if ("audio/wav".equals(type))
            return header.length >= 12 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
        if ("audio/mpeg".equals(type))
            return header.length >= 3 && ((header[0] == 'I' && header[1] == 'D' && header[2] == '3')
                    || ((header[0] & 255) == 255 && (header[1] & 224) == 224));
        return false;
    }
}
