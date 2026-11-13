package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import com.englow3.tutor.helper.TutorWav;
import com.englow3.tutor.helper.TutorDelivery;

class TutorWavTest {
    @Test
    void acceptsBoundedPcmAndRefusesMalformedChunksOrWrongFormat() {
        TutorWav.validate(TutorAudioIntegrationTest.wav());
        byte[] malformed = TutorAudioIntegrationTest.wav();
        ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Integer.MAX_VALUE);
        assertThatThrownBy(() -> TutorWav.validate(malformed)).hasMessageContaining("WAV");
        byte[] stereo = TutorAudioIntegrationTest.wav();
        stereo[22] = 2;
        assertThatThrownBy(() -> TutorWav.validate(stereo)).hasMessageContaining("WAV");
        assertThatThrownBy(() -> TutorWav.validate(new byte[12])).hasMessageContaining("WAV");
    }

    @Test
    void nullAndLiteralSentinelCannotShareARequestFingerprint() {
        assertThat(TutorDelivery.fingerprint((Object) null)).isNotEqualTo(TutorDelivery.fingerprint("<null>"));
    }
}
