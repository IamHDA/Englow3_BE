package com.englow3.exam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.*;
import com.englow3.exam.dto.result.*;

public interface AdminExamService {
    AuthoringResult authoringDetail(java.util.UUID id);

    AuthoringResult saveAuthoring(SaveAuthoringCommand command);

    ExamResult create(CreateExamCommand command);

    Page<ExamListItemResult> search(SearchExamCommand command, Pageable pageable);

    ExamDetailResult detail(ExamDetailCommand command);

    ExamResult update(UpdateExamCommand command);
}
