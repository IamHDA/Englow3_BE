package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamMediaResult;

public interface ExamContentService {
    ExamMediaResult uploadMedia(UUID examId, MultipartFile file);

    ExamDetailResult replaceContent(UpdateExamContentCommand command);
}
