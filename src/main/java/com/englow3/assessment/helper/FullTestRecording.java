package com.englow3.assessment.helper;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import com.englow3.shared.error.BadRequestException;

/** Full-test audio uses the browser recorder's bounded 16 kHz mono PCM format. */
public final class FullTestRecording {
    private FullTestRecording() {
    }

    public static void validate(byte[] audio, int seconds) {
        if (audio == null || audio.length < 44 || audio.length > 10485760 || !tag(audio, 0).equals("RIFF")
                || !tag(audio, 8).equals("WAVE"))
            invalid();
        var b = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(b.getInt(4)) != audio.length - 8)
            invalid();
        boolean format = false;
        long size = -1;
        for (int offset = 12; offset + 8 <= audio.length;) {
            long length = Integer.toUnsignedLong(b.getInt(offset + 4));
            if (length > audio.length - offset - 8)
                invalid();
            if (tag(audio, offset).equals("fmt ")) {
                if (format || length < 16 || b.getShort(offset + 8) != 1 || b.getShort(offset + 10) != 1
                        || b.getInt(offset + 12) != 16000 || b.getInt(offset + 16) != 32000
                        || b.getShort(offset + 20) != 2 || b.getShort(offset + 22) != 16)
                    invalid();
                format = true;
            }
            if (tag(audio, offset).equals("data")) {
                if (size >= 0 || length % 2 != 0)
                    invalid();
                size = length;
            }
            offset += 8 + (int) length + ((int) length % 2);
        }
        if (!format || size < 32000 || size > (seconds + 1L) * 32000)
            invalid();
    }

    private static String tag(byte[] audio, int offset) {
        return new String(audio, offset, 4, StandardCharsets.US_ASCII);
    }

    private static void invalid() {
        throw new BadRequestException("RECORDING_INVALID",
                "Record PCM WAV audio within this full-test part's time limit");
    }
}
