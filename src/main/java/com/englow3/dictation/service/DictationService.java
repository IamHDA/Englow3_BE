package com.englow3.dictation.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.dictation.dto.command.SubmitDictationCommand;
import com.englow3.dictation.dto.result.DictationLessonDetailResult;
import com.englow3.dictation.dto.result.DictationLessonSummaryResult;
import com.englow3.dictation.dto.result.DictationSubmissionResult;

public interface DictationService {
    Page<DictationLessonSummaryResult> searchPublished(String topic, String title, Pageable pageable);

    DictationLessonDetailResult lessonDetail(UUID lessonId);

    DictationSubmissionResult submit(SubmitDictationCommand command);
}
