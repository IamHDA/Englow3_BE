package com.englow3.speaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;

class SpeakingAudioKeysTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ATTEMPT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void putsEachRecordingUnderItsOwnersFolder() {
        String key = SpeakingAudioKeys.audioKey(USER_ID, ATTEMPT_ID, "audio/wav");

        assertThat(key).isEqualTo("speaking/%s/%s.wav".formatted(USER_ID, ATTEMPT_ID));
    }

    @Test
    void namesTheFileAfterItsFormat() {
        assertThat(SpeakingAudioKeys.audioKey(USER_ID, ATTEMPT_ID, "audio/ogg")).endsWith(".ogg");
        assertThat(SpeakingAudioKeys.audioKey(USER_ID, ATTEMPT_ID, "audio/x-wav")).endsWith(".wav");
    }

    /** The same attempt uploading again overwrites its own recording, which is what retrying means. */
    @Test
    void givesOneAttemptOneKey() {
        assertThat(SpeakingAudioKeys.audioKey(USER_ID, ATTEMPT_ID, "audio/wav"))
                .isEqualTo(SpeakingAudioKeys.audioKey(USER_ID, ATTEMPT_ID, "audio/wav"));
    }

    @Test
    void acceptsTheFormatsTheAssessmentServiceTakes() {
        assertThatCode(() -> SpeakingAudioKeys.requireAcceptedContentType("audio/wav")).doesNotThrowAnyException();
        assertThatCode(() -> SpeakingAudioKeys.requireAcceptedContentType("audio/ogg")).doesNotThrowAnyException();
    }

    /**
     * Refused at the door rather than at assessment time. Handing back an upload URL, letting someone record, and only
     * then saying the format was never going to work wastes their effort to reach the same answer.
     */
    @Test
    void refusesAFormatTheAssessmentCouldNeverRead() {
        assertThatThrownBy(() -> SpeakingAudioKeys.requireAcceptedContentType("audio/mpeg"))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("SPEAKING_UNSUPPORTED_AUDIO_TYPE");
    }

    @Test
    void refusesARequestThatNamesNoFormatAtAll() {
        assertThatThrownBy(() -> SpeakingAudioKeys.requireAcceptedContentType(null))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("SPEAKING_UNSUPPORTED_AUDIO_TYPE");
    }

    @Test
    void acceptsARecordingOfAnOrdinarySize() {
        assertThatCode(() -> SpeakingAudioKeys.requireAcceptedSize(480_000)).doesNotThrowAnyException();
    }

    /**
     * The gap this closes. A presigned PUT goes straight to storage and never touches this application, so a size that
     * is not refused here and not bound into the signature is not refused anywhere at all.
     */
    @Test
    void refusesARecordingLargerThanTheAssessmentCouldEverRead() {
        assertThatThrownBy(() -> SpeakingAudioKeys.requireAcceptedSize(SpeakingAudioKeys.MAX_AUDIO_BYTES + 1))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("SPEAKING_RECORDING_TOO_LARGE");
    }

    @Test
    void acceptsExactlyTheLimit() {
        assertThatCode(() -> SpeakingAudioKeys.requireAcceptedSize(SpeakingAudioKeys.MAX_AUDIO_BYTES))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAnEmptyOrNegativeRecording() {
        assertThatThrownBy(() -> SpeakingAudioKeys.requireAcceptedSize(0)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("SPEAKING_EMPTY_RECORDING");
        assertThatThrownBy(() -> SpeakingAudioKeys.requireAcceptedSize(-1)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("SPEAKING_EMPTY_RECORDING");
    }
}
