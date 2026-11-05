package com.englow3.translation.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.translation.dto.command.SubmitTranslationCommand;
import com.englow3.translation.dto.result.TranslationAttemptResult;
import com.englow3.translation.dto.result.TranslationSetDetailResult;
import com.englow3.translation.dto.result.TranslationSetSummaryResult;

public interface TranslationService {

    Page<TranslationSetSummaryResult> searchSets(String topic, String title, Pageable pageable);

    TranslationSetDetailResult setDetail(UUID setId);

    /** Records the learner's translation and asks for it to be graded. The grade arrives later; poll the attempt. */
    TranslationAttemptResult submit(SubmitTranslationCommand command);

    TranslationAttemptResult attempt(UUID attemptId);

    Page<TranslationAttemptResult> history(Pageable pageable);
}
