package com.englow3.learning.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.learning.dto.command.SubmitDictationCommand;
import com.englow3.learning.dto.result.*;

public interface DictationService {
    Page<DictationLessonSummaryResult> searchPublished(String topic, String title, Pageable pageable);

    DictationLessonDetailResult lessonDetail(UUID lessonId);

    DictationSubmissionResult submit(SubmitDictationCommand command);
}
