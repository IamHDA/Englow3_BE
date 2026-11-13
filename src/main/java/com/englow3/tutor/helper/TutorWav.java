package com.englow3.tutor.helper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import com.englow3.shared.error.BadRequestException;

/** Tutor voice questions are bounded PCM: 16 kHz, mono, 16-bit, at most one minute. */
public final class TutorWav {
    public static final int MAX_BYTES = 2_100_000;

    private TutorWav() {
    }

    public static void validate(byte[] audio) {
        if (audio.length < 44 || audio.length > MAX_BYTES || !tag(audio, 0).equals("RIFF")
                || !tag(audio, 8).equals("WAVE"))
            invalid();
        ByteBuffer buffer = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(buffer.getInt(4)) != audio.length - 8)
            invalid();
        boolean format = false;
        long data = -1;
        for (int offset = 12; offset + 8 <= audio.length;) {
            long length = Integer.toUnsignedLong(buffer.getInt(offset + 4));
            if (length > audio.length - offset - 8)
                invalid();
            String tag = tag(audio, offset);
            if (tag.equals("fmt ")) {
                if (format)
                    invalid();
                if (length < 16 || buffer.getShort(offset + 8) != 1 || buffer.getShort(offset + 10) != 1
                        || buffer.getInt(offset + 12) != 16000 || buffer.getInt(offset + 16) != 32000
                        || buffer.getShort(offset + 20) != 2 || buffer.getShort(offset + 22) != 16)
                    invalid();
                format = true;
            }
            if (tag.equals("data")) {
                if (data >= 0 || length % 2 != 0)
                    invalid();
                data = length;
            }
            offset += 8 + (int) length + ((int) length % 2);
        }
        if (!format || data < 32000 || data > 61 * 32000L)
            invalid();
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static void invalid() {
        throw new BadRequestException("TUTOR_AUDIO_INVALID", "Record WAV PCM audio between 1 and 60 seconds");
    }
}
