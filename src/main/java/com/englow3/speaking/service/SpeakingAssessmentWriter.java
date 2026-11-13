package com.englow3.speaking.service;

import java.util.UUID;

import com.englow3.speaking.service.SpeechAssessmentParser.Assessment;

public interface SpeakingAssessmentWriter {
    void storeAssessment(UUID attemptId, Assessment assessment);

    void markFailed(UUID attemptId, String errorCode);
}
