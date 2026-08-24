package com.englow3.ai.speaking;

interface SpeechAssessmentClient {

    SpeechAssessmentResult assess(byte[] audio, String contentType, String locale, String referenceText);
}
