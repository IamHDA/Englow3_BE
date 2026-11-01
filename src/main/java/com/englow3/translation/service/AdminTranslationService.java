package com.englow3.translation.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.translation.dto.command.SaveTranslationSetCommand;
import com.englow3.translation.dto.result.TranslationAuthoringResult;
import com.englow3.translation.dto.result.TranslationSetReviewResult;
import com.englow3.translation.entity.TranslationSetStatus;

public interface AdminTranslationService {

    Page<TranslationSetReviewResult> searchForAuthoring(TranslationSetStatus status, String title, Pageable pageable);

    TranslationSetReviewResult submitForReview(UUID setId);

    TranslationSetReviewResult approve(UUID setId);

    TranslationSetReviewResult reject(UUID setId, String note);

    TranslationSetReviewResult publish(UUID setId);

    TranslationSetReviewResult restore(UUID setId);

    TranslationSetReviewResult archive(UUID setId);

    TranslationAuthoringResult authoringDetail(UUID setId);

    TranslationAuthoringResult saveAuthoring(SaveTranslationSetCommand command);
}
