package com.englow3.exam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.SearchQuestionBankCommand;
import com.englow3.exam.dto.result.QuestionBankItemResult;

public interface QuestionBankService {
    Page<QuestionBankItemResult> searchQuestionBank(SearchQuestionBankCommand command, Pageable pageable);
}
