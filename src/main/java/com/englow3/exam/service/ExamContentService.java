package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamMediaResult;

public interface ExamContentService {
    ExamMediaResult uploadMedia(UUID examId, MultipartFile file);

    /** The same check and storage as a browser upload, for content the server made itself. Network I/O. */
    ExamMediaResult uploadMedia(UUID examId, byte[] content, String contentType);

    ExamDetailResult replaceContent(UpdateExamContentCommand command);
}
