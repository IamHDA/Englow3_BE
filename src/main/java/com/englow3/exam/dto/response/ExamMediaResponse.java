package com.englow3.exam.dto.response;

import com.englow3.exam.dto.result.ExamMediaResult;

public record ExamMediaResponse(String objectKey) {

    public static ExamMediaResponse from(ExamMediaResult result) {
        return new ExamMediaResponse(result.objectKey());
    }
}
