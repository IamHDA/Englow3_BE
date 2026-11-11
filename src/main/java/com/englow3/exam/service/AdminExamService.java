package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.SaveAuthoringCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.result.AuthoringResult;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;

public interface AdminExamService {
    void requireAuthoringAccess(UUID id);

    AuthoringResult authoringDetail(UUID id);

    AuthoringResult generatedAuthoringDetail(UUID id, UUID authorId);

    AuthoringResult saveAuthoring(SaveAuthoringCommand command);

    /** As {@link #saveAuthoring(SaveAuthoringCommand)}, creating a new paper for {@code authorId}. */
    AuthoringResult saveAuthoring(SaveAuthoringCommand command, UUID authorId);

    ExamResult create(CreateExamCommand command);

    Page<ExamListItemResult> search(SearchExamCommand command, Pageable pageable);

    ExamDetailResult detail(ExamDetailCommand command);

    ExamResult update(UpdateExamCommand command);
}
