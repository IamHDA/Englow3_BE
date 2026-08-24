package com.englow3.ai.speaking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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
        try {
            if (audio == null || audio.length == 0) {
                throw new AiProviderException("SPEECH_EMPTY_AUDIO", "Audio payload is empty", false);
            }
            MediaType mediaType = parseSupportedMediaType(contentType);
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart.part("audio", namedResource(audio, mediaType)).contentType(mediaType);
            multipart.part("locale", locale);
            if (referenceText != null && !referenceText.isBlank()) {
                multipart.part("reference_text", referenceText);
            }
            ServiceResponse response = restClient.post().uri("/internal/v1/speech/assess")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(multipart.build()).retrieve()
                    .body(ServiceResponse.class);
            if (response == null) {
                throw new AiProviderException("SPEECH_EMPTY_RESPONSE", "The AI service returned no assessment", true);
            }
            return mapResponse(response);
        } catch (RestClientResponseException ex) {
            throw serviceError(ex);
        } catch (ResourceAccessException ex) {
            throw new AiProviderException("AI_SERVICE_UNAVAILABLE", "The AI service is unavailable", true, ex);
        } catch (RestClientException ex) {
            throw invalidResponse(ex);
        }
    }

    private SpeechAssessmentResult mapResponse(ServiceResponse response) {
        if (response.recognizedText() == null || response.recognizedText().isBlank() || response.raw() == null) {
            throw invalidResponse(null);
        }
        validateScore(response.accuracy());
        validateScore(response.fluency());
        validateScore(response.completeness());
        validateScore(response.prosody());
        validateScore(response.pronunciation());

        List<SpeechAssessmentResult.WordAssessment> words = new ArrayList<>();
        if (response.words() != null) {
            for (WordResponse word : response.words()) {
                if (word == null || word.word() == null || word.word().isBlank() || isNegative(word.offsetMs())
                        || isNegative(word.durationMs())) {
                    throw invalidResponse(null);
                }
                validateScore(word.accuracy());
                words.add(new SpeechAssessmentResult.WordAssessment(word.word(), word.accuracy(), word.errorType(),
                        word.offsetMs(), word.durationMs()));
            }
        }
        return new SpeechAssessmentResult(response.recognizedText(), response.accuracy(), response.fluency(),
                response.completeness(), response.prosody(), response.pronunciation(), response.requestId(),
                List.copyOf(words), response.raw());
    }

    private void validateScore(Double score) {
        if (score != null && (!Double.isFinite(score) || score < 0 || score > 100)) {
            throw invalidResponse(null);
        }
    }

    private boolean isNegative(Integer value) {
        return value != null && value < 0;
    }

    private MediaType parseSupportedMediaType(String contentType) {
        final MediaType mediaType;
        try {
            String baseContentType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
            mediaType = MediaType.parseMediaType(baseContentType);
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException("SPEECH_UNSUPPORTED_AUDIO_TYPE", "Unsupported audio content type", false, ex);
        }
        String normalized = mediaType.getType() + "/" + mediaType.getSubtype();
        if (!normalized.equals("audio/wav") && !normalized.equals("audio/x-wav") && !normalized.equals("audio/ogg")) {
            throw new AiProviderException("SPEECH_UNSUPPORTED_AUDIO_TYPE", "Unsupported audio content type", false);
        }
        return mediaType;
    }

    private ByteArrayResource namedResource(byte[] audio, MediaType mediaType) {
        String filename = mediaType.getSubtype().contains("ogg") ? "audio.ogg" : "audio.wav";
        return new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private AiProviderException serviceError(RestClientResponseException ex) {
        boolean retryable = isRetryableStatus(ex.getStatusCode().value());
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

    private AiProviderException invalidResponse(Throwable cause) {
        return new AiProviderException("AI_SERVICE_INVALID_RESPONSE", "The AI service returned an invalid response",
                true, cause);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
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
