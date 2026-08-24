package com.englow3.ai.speaking;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.englow3.ai.foundation.AiProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
class AzureSpeechAssessmentClient implements SpeechAssessmentClient {

    private final RestClient restClient;
    private final SpeechProperties properties;
    private final ObjectMapper objectMapper;

    AzureSpeechAssessmentClient(RestClient speechRestClient, SpeechProperties properties, ObjectMapper objectMapper) {
        this.restClient = speechRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SpeechAssessmentResult assess(byte[] audio, String contentType, String locale, String referenceText) {
        if (!properties.enabled() || properties.apiKey().isBlank()) {
            throw new AiProviderException("SPEECH_DISABLED", "Speech assessment is disabled", false);
        }
        try {
            ResponseEntity<JsonNode> response = restClient.post()
                    .uri(uri -> uri.path("/stt/speech/recognition/conversation/cognitiveservices/v1")
                            .queryParam("language", locale).queryParam("format", "detailed").build())
                    .header("Ocp-Apim-Subscription-Key", properties.apiKey())
                    .header("Pronunciation-Assessment", assessmentHeader(referenceText))
                    .header(HttpHeaders.CONTENT_TYPE, contentType).accept(MediaType.APPLICATION_JSON).body(audio)
                    .retrieve().toEntity(JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !"Success".equals(body.path("RecognitionStatus").asText())) {
                throw new AiProviderException("SPEECH_RECOGNITION_FAILED", "Speech could not be recognized", false);
            }
            JsonNode best = body.path("NBest").path(0);
            JsonNode scores = best.path("PronunciationAssessment");
            return new SpeechAssessmentResult(best.path("Display").asText(body.path("DisplayText").asText()),
                    number(scores, "AccuracyScore"), number(scores, "FluencyScore"),
                    number(scores, "CompletenessScore"), number(scores, "ProsodyScore"), number(scores, "PronScore"),
                    response.getHeaders().getFirst("X-RequestId"), words(best), body);
        } catch (RestClientResponseException ex) {
            boolean retryable = ex.getStatusCode().value() == 429 || ex.getStatusCode().is5xxServerError();
            throw new AiProviderException("SPEECH_PROVIDER_HTTP_" + ex.getStatusCode().value(),
                    "Speech provider rejected the request", retryable, ex);
        } catch (ResourceAccessException ex) {
            throw new AiProviderException("SPEECH_PROVIDER_UNAVAILABLE", "Speech provider is unavailable", true, ex);
        }
    }

    private String assessmentHeader(String referenceText) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (referenceText != null && !referenceText.isBlank()) {
            config.put("ReferenceText", referenceText);
        }
        config.put("GradingSystem", "HundredMark");
        config.put("Granularity", "Phoneme");
        config.put("Dimension", "Comprehensive");
        config.put("EnableMiscue", true);
        config.put("EnableProsodyAssessment", true);
        try {
            byte[] json = objectMapper.writeValueAsString(config).getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not build speech assessment parameters", ex);
        }
    }

    private List<SpeechAssessmentResult.WordAssessment> words(JsonNode best) {
        List<SpeechAssessmentResult.WordAssessment> words = new ArrayList<>();
        for (JsonNode word : best.path("Words")) {
            JsonNode score = word.path("PronunciationAssessment");
            words.add(new SpeechAssessmentResult.WordAssessment(word.path("Word").asText(),
                    number(score, "AccuracyScore"), score.path("ErrorType").asText(null),
                    ticksToMillis(word.path("Offset")), ticksToMillis(word.path("Duration"))));
        }
        return List.copyOf(words);
    }

    private Double number(JsonNode node, String field) {
        return node.has(field) && node.path(field).isNumber() ? node.path(field).doubleValue() : null;
    }

    private Integer ticksToMillis(JsonNode value) {
        return value.isNumber() ? Math.toIntExact(value.longValue() / 10_000L) : null;
    }
}
