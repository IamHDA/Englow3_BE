package com.englow3.ai.speaking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.englow3.ai.foundation.AiProviderException;

class SpeakingAssessmentJobHandlerTest {

    @Test
    void acceptsWaveSignatureForWaveContentType() {
        byte[] audio = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

        assertThatCode(() -> SpeakingAssessmentJobHandler.validateMagic(audio,
                "audio/wav; codecs=audio/pcm; samplerate=16000")).doesNotThrowAnyException();
    }

    @Test
    void acceptsOggSignatureForOpusContentType() {
        byte[] audio = {'O', 'g', 'g', 'S', 0};

        assertThatCode(() -> SpeakingAssessmentJobHandler.validateMagic(audio, "audio/ogg; codecs=opus"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredFormat() {
        byte[] executable = {'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        assertThatThrownBy(() -> SpeakingAssessmentJobHandler.validateMagic(executable,
                "audio/wav; codecs=audio/pcm; samplerate=16000"))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).code())
                .isEqualTo("SPEAKING_AUDIO_SIGNATURE_INVALID");
    }
}
