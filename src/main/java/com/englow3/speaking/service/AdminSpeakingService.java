package com.englow3.speaking.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.speaking.dto.command.CreateSpeakingPromptCommand;
import com.englow3.speaking.dto.result.SpeakingPromptReviewResult;
import com.englow3.speaking.entity.SpeakingPromptStatus;

public interface AdminSpeakingService {
    SpeakingPromptReviewResult create(CreateSpeakingPromptCommand command);

    Page<SpeakingPromptReviewResult> searchForAuthoring(SpeakingPromptStatus status, String title, Pageable pageable);

    SpeakingPromptReviewResult submitForReview(UUID promptId);

    SpeakingPromptReviewResult approve(UUID promptId);

    SpeakingPromptReviewResult reject(UUID promptId, String note);

    SpeakingPromptReviewResult publish(UUID promptId);

    SpeakingPromptReviewResult archive(UUID promptId);
}
