package com.englow3.speaking.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.speaking.dto.command.CreateSpeakingPromptCommand;
import com.englow3.speaking.dto.command.SaveAuthoringCommand;
import com.englow3.speaking.dto.result.AuthoringResult;
import com.englow3.speaking.dto.result.SpeakingPromptReviewResult;
import com.englow3.speaking.entity.SpeakingPromptStatus;

public interface AdminSpeakingService {
    AuthoringResult authoringDetail(UUID id);

    AuthoringResult saveAuthoring(SaveAuthoringCommand command);

    SpeakingPromptReviewResult create(CreateSpeakingPromptCommand command);

    Page<SpeakingPromptReviewResult> searchForAuthoring(SpeakingPromptStatus status, String title, Pageable pageable);

    SpeakingPromptReviewResult submitForReview(UUID promptId);

    SpeakingPromptReviewResult approve(UUID promptId);

    SpeakingPromptReviewResult reject(UUID promptId, String note);

    SpeakingPromptReviewResult publish(UUID promptId);

    SpeakingPromptReviewResult archive(UUID promptId);

    SpeakingPromptReviewResult restore(UUID promptId);
}
