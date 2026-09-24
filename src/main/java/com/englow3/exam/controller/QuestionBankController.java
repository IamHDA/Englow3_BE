package com.englow3.exam.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.command.SearchQuestionBankCommand;
import com.englow3.exam.dto.request.SearchQuestionBankRequest;
import com.englow3.exam.dto.response.QuestionBankItemResponse;
import com.englow3.exam.service.AdminExamService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;

/**
 * The bank is not a store of its own - it is every question ever authored, searchable, and "choosing" one is a copy
 * (Phase 5): the frontend re-sends this row's fields in the same {@code PUT /content} payload it already uses, with
 * {@code sourceQuestionId} set. There is no copy endpoint here for that reason.
 */
@RestController
@RequestMapping("/api/admin/question-bank")
@PreAuthorize("hasRole('ADMIN')")
class QuestionBankController {

    private final AdminExamService adminExamService;

    QuestionBankController(AdminExamService adminExamService) {
        this.adminExamService = adminExamService;
    }

    /** {@code orderNo} is the sort default, not {@code createdAt} - {@code Question} maps no timestamp. */
    @GetMapping
    ResponseEntity<PageResponse<QuestionBankItemResponse>> search(@Valid SearchQuestionBankRequest request,
            @PageableDefault(size = 20, sort = "orderNo", direction = Sort.Direction.ASC) Pageable pageable) {
        SearchQuestionBankCommand command = new SearchQuestionBankCommand(request.skillType(),
                request.difficultyLevel(), request.keyword());

        return ResponseEntity.ok(PageResponse
                .from(adminExamService.searchQuestionBank(command, pageable).map(QuestionBankItemResponse::from)));
    }
}
