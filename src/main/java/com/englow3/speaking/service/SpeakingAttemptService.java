package com.englow3.speaking.service;

import java.util.List;
import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingAttemptResult;
import com.englow3.speaking.dto.result.SpeakingUploadTicket;

public interface SpeakingAttemptService {
    SpeakingUploadTicket startAttempt(UUID promptId, String contentType, long contentLength);

    SpeakingAttemptResult submitAttempt(UUID attemptId);

    SpeakingAttemptResult attemptResult(UUID attemptId);

    List<SpeakingAttemptResult> attemptHistory(UUID promptId);
}
