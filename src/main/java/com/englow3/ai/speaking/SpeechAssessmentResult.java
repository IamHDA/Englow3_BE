package com.englow3.ai.speaking;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

record SpeechAssessmentResult(String recognizedText, Double accuracy, Double fluency, Double completeness,
        Double prosody, Double pronunciation, String requestId, List<WordAssessment> words, JsonNode raw) {

    record WordAssessment(String word, Double accuracy, String errorType, Integer offsetMs, Integer durationMs) {
    }
}
