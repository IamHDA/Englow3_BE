package com.englow3.exam.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.SearchQuestionBankCommand;
import com.englow3.exam.dto.result.QuestionBankItemResult;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.service.QuestionBankService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository questionOptionRepo;

    @Transactional(readOnly = true)
    public Page<QuestionBankItemResult> searchQuestionBank(SearchQuestionBankCommand command, Pageable pageable) {
        Page<Question> page = questionRepo.search(command.skillType(), command.difficultyLevel(), command.keyword(),
                pageable);
        List<UUID> questionIds = page.getContent().stream().map(Question::getId).toList();
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionIds.isEmpty() ? Map.of()
                : questionOptionRepo.findAllForQuestions(questionIds).stream()
                        .collect(Collectors.groupingBy(QuestionOption::getQuestionId));
        return page.map(q -> QuestionBankItemResult.of(q, optionsByQuestion.getOrDefault(q.getId(), List.of())));
    }
}
