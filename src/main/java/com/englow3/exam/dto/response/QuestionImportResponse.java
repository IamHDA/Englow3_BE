package com.englow3.exam.dto.response;

import java.util.List;

import com.englow3.exam.dto.result.QuestionImportResult;
import com.englow3.exam.entity.QuestionType;

public record QuestionImportResponse(List<ImportedQuestionResponse> questions, List<RowErrorResponse> errors) {

    public static QuestionImportResponse from(QuestionImportResult result) {
        return new QuestionImportResponse(result.questions().stream().map(ImportedQuestionResponse::from).toList(),
                result.errors().stream().map(RowErrorResponse::from).toList());
    }

    public record ImportedQuestionResponse(int rowNumber, QuestionType questionType, String content,
            String explanation, List<ImportedOptionResponse> options) {

        static ImportedQuestionResponse from(QuestionImportResult.ImportedQuestion result) {
            return new ImportedQuestionResponse(result.rowNumber(), result.questionType(), result.content(),
                    result.explanation(), result.options().stream().map(ImportedOptionResponse::from).toList());
        }
    }

    public record ImportedOptionResponse(String content, boolean correct) {

        static ImportedOptionResponse from(QuestionImportResult.ImportedOption result) {
            return new ImportedOptionResponse(result.content(), result.correct());
        }
    }

    public record RowErrorResponse(int rowNumber, String code, String message) {

        static RowErrorResponse from(QuestionImportResult.RowError result) {
            return new RowErrorResponse(result.rowNumber(), result.code(), result.message());
        }
    }
}
