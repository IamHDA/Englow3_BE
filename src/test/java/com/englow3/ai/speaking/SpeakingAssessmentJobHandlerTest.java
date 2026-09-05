package com.englow3.ai.speaking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpeakingAssessmentJobHandlerTest {
    private byte[] wave(int channels, int sampleRate, int bits) {
        ByteBuffer buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) channels).putInt(sampleRate).putInt(sampleRate * channels * bits / 8)
                .putShort((short) (channels * bits / 8)).putShort((short) bits)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(0);
        return buffer.array();
    }

    @Nested
    class Success {

        @Test
        void acceptsWaveSignatureForWaveContentType() {
            byte[] audio = wave(1, 16_000, 16);

            assertThatCode(() -> SpeakingAssessmentJobHandler.validateMagic(audio,
                    "audio/wav; codecs=audio/pcm; samplerate=16000")).doesNotThrowAnyException();
        }

        @Test
        void acceptsOggSignatureForOpusContentType() {
            byte[] audio = "OggS0000OpusHead".getBytes(StandardCharsets.US_ASCII);

            assertThatCode(() -> SpeakingAssessmentJobHandler.validateMagic(audio, "audio/ogg; codecs=opus"))
                    .doesNotThrowAnyException();
        }

        @Test
        void normalizesRecurringWordAndPhonemeErrors() {
            org.assertj.core.api.Assertions.assertThat(SpeakingAssessmentPersistence.normalizeUnit("Hello!"))
                    .isEqualTo("hello");
            org.assertj.core.api.Assertions
                    .assertThat(SpeakingAssessmentPersistence.normalizeError("Mispronunciation", 90d))
                    .isEqualTo("MISPRONUNCIATION");
            org.assertj.core.api.Assertions.assertThat(SpeakingAssessmentPersistence.normalizeError(null, 79.9d))
                    .isEqualTo("LOW_ACCURACY");
            org.assertj.core.api.Assertions.assertThat(SpeakingAssessmentPersistence.normalizeError("None", 95d))
                    .isNull();
        }

    }

    @Nested
    class Failure {

        @Test
        void rejectsContentThatDoesNotMatchDeclaredFormat() {
            byte[] executable = { 'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

            assertThatThrownBy(() -> SpeakingAssessmentJobHandler.validateMagic(executable,
                    "audio/wav; codecs=audio/pcm; samplerate=16000")).isInstanceOf(AiProviderException.class)
                            .extracting(exception -> ((AiProviderException) exception).code())
                            .isEqualTo("SPEAKING_AUDIO_SIGNATURE_INVALID");
        }

        @Test
        void rejectsWaveWithUnsupportedEncoding() {
            byte[] stereo = wave(2, 44_100, 16);

            assertThatThrownBy(() -> SpeakingAssessmentJobHandler.validateMagic(stereo,
                    "audio/wav; codecs=audio/pcm; samplerate=16000")).isInstanceOf(AiProviderException.class)
                            .extracting(exception -> ((AiProviderException) exception).code())
                            .isEqualTo("SPEAKING_AUDIO_ENCODING_INVALID");
        }

        @Test
        void rejectsBlankLanguageModelFeedback() throws Exception {
            var structured = new ObjectMapper().readTree("{\"grammarFeedback\":\"   \"}");

            assertThatThrownBy(() -> SpeakingAssessmentJobHandler.requiredFeedback(structured, "grammarFeedback"))
                    .isInstanceOf(AiProviderException.class)
                    .extracting(exception -> ((AiProviderException) exception).code())
                    .isEqualTo("AI_SPEAKING_FEEDBACK_SCHEMA_INVALID");
        }

    }

}
