package com.englow3.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The only decision in this client worth pinning down: whether a failure is worth repeating. Getting it wrong either
 * burns the retry budget on a recording that can never be scored, or gives up on a provider that was down for a second.
 */
class SpeechAssessmentClientTest {

    @Test
    void retriesWhenTheServiceItselfFailed() {
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.INTERNAL_SERVER_ERROR)).isTrue();
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.BAD_GATEWAY)).isTrue();
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.SERVICE_UNAVAILABLE)).isTrue();
    }

    /** The adapter read the request and refused it. The same bytes will be refused again. */
    @Test
    void givesUpWhenTheRequestItselfWasRefused() {
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.UNSUPPORTED_MEDIA_TYPE)).isFalse();
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.PAYLOAD_TOO_LARGE)).isFalse();
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.UNPROCESSABLE_ENTITY)).isFalse();
    }

    /**
     * A missing internal key is a deployment fault, not a bad recording - but retrying cannot fix it either, and
     * failing fast puts the real reason in the job row where an operator will see it.
     */
    @Test
    void givesUpWhenTheServiceRefusesTheSharedSecret() {
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.UNAUTHORIZED)).isFalse();
    }

    /** The two 4xx codes that mean "later" rather than "no". */
    @Test
    void retriesOnTimeoutAndRateLimit() {
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.REQUEST_TIMEOUT)).isTrue();
        assertThat(SpeechAssessmentClient.retryable(HttpStatus.TOO_MANY_REQUESTS)).isTrue();
    }
}
