package com.englow3.assessment.helper;

import static org.assertj.core.api.Assertions.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FullTestRecordingTest {
    private byte[] wav(int seconds) {
        var b = ByteBuffer.allocate(44 + seconds * 32000).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(b.capacity() - 8)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16).putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2)
                .putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(seconds * 32000);
        return b.array();
    }

    @Test
    void acceptsPartTwoWithinItsLimit() {
        assertThatCode(() -> FullTestRecording.validate(wav(120), 120)).doesNotThrowAnyException();
    }

    @Test
    void rejectsLongerRecordingsAndForgedHeaders() {
        assertThatThrownBy(() -> FullTestRecording.validate(wav(122), 120)).hasMessageContaining("limit");
        var data = wav(10);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(28, 16000);
        assertThatThrownBy(() -> FullTestRecording.validate(data, 120)).isInstanceOf(RuntimeException.class);
    }
}
