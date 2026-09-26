package com.englow3.speaking.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.speaking.dto.result.*;

public interface SpeakingService {
    Page<SpeakingPromptResult> searchPublished(String category, String title, Pageable pageable);

    SpeakingPromptResult promptDetail(UUID promptId);

    SpeakingUploadTicket startAttempt(UUID promptId, String contentType, long contentLength);

    SpeakingAttemptResult submitAttempt(UUID attemptId);

    SpeakingAttemptResult attemptResult(UUID attemptId);

    List<SpeakingAttemptResult> attemptHistory(UUID promptId);
}
