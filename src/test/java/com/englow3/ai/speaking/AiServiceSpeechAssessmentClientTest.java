package com.englow3.ai.speaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiServiceSpeechAssessmentClientTest {

    @Test
    void mapsAValidSpeechAssessmentContract() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/speech/assess"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        {
                          "recognized_text":"Hello",
                          "accuracy":90.0,
                          "fluency":80.0,
                          "completeness":100.0,
                          "prosody":75.0,
                          "pronunciation":88.0,
                          "request_id":"request-1",
                          "words":[{"word":"Hello","accuracy":90.0,"error_type":"None","offset_ms":1,"duration_ms":2}],
                          "raw":{"RecognitionStatus":"Success"}
                        }
                        """, MediaType.APPLICATION_JSON));
        AiServiceSpeechAssessmentClient client = client(builder.build(), true);

        SpeechAssessmentResult result = client.assess(new byte[] { 1, 2 },
                "audio/wav; codecs=audio/pcm; samplerate=16000", "en-US", "Hello");

        assertThat(result.recognizedText()).isEqualTo("Hello");
        assertThat(result.pronunciation()).isEqualTo(88.0);
        assertThat(result.words()).singleElement().satisfies(word -> assertThat(word.offsetMs()).isEqualTo(1));
        server.verify();
    }

    @Test
    void rejectsEmptyAndUnsupportedAudioBeforeCallingTheService() {
        AiServiceSpeechAssessmentClient client = client(RestClient.create("http://localhost"), true);

        assertThatThrownBy(() -> client.assess(new byte[0], "audio/wav", "en-US", null))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertProviderError(error, "SPEECH_EMPTY_AUDIO", false));
        assertThatThrownBy(() -> client.assess(new byte[] { 1 }, "audio/mpeg", "en-US", null))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertProviderError(error, "SPEECH_UNSUPPORTED_AUDIO_TYPE", false));
    }

    @Test
    void rejectsInvalidScoresReturnedByTheAiService() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/speech/assess")).andRespond(withSuccess("""
                {"recognized_text":"Hello","accuracy":101,"words":[],"raw":{}}
                """, MediaType.APPLICATION_JSON));
        AiServiceSpeechAssessmentClient client = client(builder.build(), true);

        assertThatThrownBy(() -> client.assess(new byte[] { 1 }, "audio/ogg; codecs=opus", "en-US", null))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertProviderError(error, "AI_SERVICE_INVALID_RESPONSE", true));
        server.verify();
    }

    @Test
    void mapsMalformedSuccessPayloadsToAStableRetryableError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/speech/assess"))
                .andRespond(withSuccess("{invalid-json", MediaType.APPLICATION_JSON));
        AiServiceSpeechAssessmentClient client = client(builder.build(), true);

        assertThatThrownBy(() -> client.assess(new byte[] { 1 }, "audio/wav", "en-US", null))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertProviderError(error, "AI_SERVICE_INVALID_RESPONSE", true));
        server.verify();
    }

    @Test
    void preservesStructuredServiceErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai-service/internal/v1/speech/assess"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"SPEECH_PROVIDER_UNAVAILABLE","message":"Speech unavailable","retryable":true}
                        """));
        AiServiceSpeechAssessmentClient client = client(builder.build(), true);

        assertThatThrownBy(() -> client.assess(new byte[] { 1 }, "audio/wav", "en-US", null))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertProviderError(error, "SPEECH_PROVIDER_UNAVAILABLE", true));
        server.verify();
    }

    private AiServiceSpeechAssessmentClient client(RestClient restClient, boolean enabled) {
        SpeechProperties properties = new SpeechProperties(enabled, "en-US", 10_485_760, Duration.ofMinutes(10),
                Duration.ofDays(30));
        return new AiServiceSpeechAssessmentClient(restClient, properties, new ObjectMapper());
    }

    private void assertProviderError(Throwable throwable, String code, boolean retryable) {
        AiProviderException error = (AiProviderException) throwable;
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.retryable()).isEqualTo(retryable);
    }
}
