package com.englow3.ai.speaking;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
class AiServiceSpeechAssessmentClient implements SpeechAssessmentClient {

    private final RestClient restClient;
    private final SpeechProperties properties;
    private final ObjectMapper objectMapper;

    AiServiceSpeechAssessmentClient(RestClient aiServiceRestClient, SpeechProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = aiServiceRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SpeechAssessmentResult assess(byte[] audio, String contentType, String locale, String referenceText) {
        if (!properties.enabled()) {
            throw new AiProviderException("SPEECH_DISABLED", "Speech assessment is disabled", false);
        }
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("audio", namedResource(audio, contentType)).contentType(MediaType.parseMediaType(contentType));
        multipart.part("locale", locale);
        if (referenceText != null && !referenceText.isBlank()) {
            multipart.part("reference_text", referenceText);
        }
        try {
            ServiceResponse response = restClient.post().uri("/internal/v1/speech/assess")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(multipart.build()).retrieve()
                    .body(ServiceResponse.class);
            if (response == null) {
                throw new AiProviderException("SPEECH_EMPTY_RESPONSE", "The AI service returned no assessment", true);
            }
            List<SpeechAssessmentResult.WordAssessment> words = response
                    .words() == null
                            ? List.of()
                            : response
                                    .words().stream().map(word -> new SpeechAssessmentResult.WordAssessment(word.word(),
                                            word.accuracy(), word.errorType(), word.offsetMs(), word.durationMs()))
                                    .toList();
            return new SpeechAssessmentResult(response.recognizedText(), response.accuracy(), response.fluency(),
                    response.completeness(), response.prosody(), response.pronunciation(), response.requestId(), words,
                    response.raw());
        } catch (RestClientResponseException ex) {
            throw serviceError(ex);
        } catch (ResourceAccessException ex) {
            throw new AiProviderException("AI_SERVICE_UNAVAILABLE", "The AI service is unavailable", true, ex);
        }
    }

    private ByteArrayResource namedResource(byte[] audio, String contentType) {
        String filename = MediaType.valueOf("audio/ogg").includes(MediaType.parseMediaType(contentType)) ? "audio.ogg"
                : "audio.wav";
        return new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private AiProviderException serviceError(RestClientResponseException ex) {
        boolean retryable = ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError();
        try {
            ErrorResponse error = objectMapper.readValue(ex.getResponseBodyAsString(), ErrorResponse.class);
            if (error.code() != null && error.message() != null) {
                return new AiProviderException(error.code(), error.message(), error.retryable(), ex);
            }
        } catch (Exception ignored) {
            // Do not leak untrusted provider response bodies.
        }
        return new AiProviderException("AI_SERVICE_HTTP_" + ex.getStatusCode().value(),
                "The AI service rejected the speech request", retryable, ex);
    }

    private record ServiceResponse(@JsonProperty("recognized_text") String recognizedText, Double accuracy,
            Double fluency, Double completeness, Double prosody, Double pronunciation,
            @JsonProperty("request_id") String requestId, List<WordResponse> words, JsonNode raw) {
    }

    private record WordResponse(String word, Double accuracy, @JsonProperty("error_type") String errorType,
            @JsonProperty("offset_ms") Integer offsetMs, @JsonProperty("duration_ms") Integer durationMs) {
    }

    private record ErrorResponse(String code, String message, boolean retryable) {
    }
}
