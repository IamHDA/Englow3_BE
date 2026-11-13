package com.englow3.exam.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.SearchQuestionBankCommand;
import com.englow3.exam.dto.result.QuestionBankItemResult;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionAcceptedAnswer;
import com.englow3.exam.entity.QuestionMatchingAnswer;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSetOption;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.repository.QuestionAcceptedAnswerRepository;
import com.englow3.exam.repository.QuestionMatchingAnswerRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetOptionRepository;
import com.englow3.exam.service.QuestionBankService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository questionOptionRepo;
    private final QuestionAcceptedAnswerRepository acceptedAnswerRepo;
    private final QuestionMatchingAnswerRepository matchingAnswerRepo;
    private final QuestionSetOptionRepository setOptionRepo;

    /**
     * One query per answer table for the whole page, never per question. A matching question's answer is stored as the
     * id of its set's list entry; it is handed out as the entry's label, which is what a copy is saved with.
     */
    @Transactional(readOnly = true)
    public Page<QuestionBankItemResult> searchQuestionBank(SearchQuestionBankCommand command, Pageable pageable) {
        Page<Question> page = questionRepo.search(command.skillType(), command.difficultyLevel(), command.keyword(),
                pageable);
        List<UUID> questionIds = page.getContent().stream().map(Question::getId).toList();
        if (questionIds.isEmpty()) {
            return page.map(q -> QuestionBankItemResult.of(q, List.of(), List.of(), List.of(), List.of()));
        }
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionOptionRepo.findAllForQuestions(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));
        Map<UUID, List<QuestionAcceptedAnswer>> acceptedByQuestion = acceptedAnswerRepo.findAllForQuestions(questionIds)
                .stream().collect(Collectors.groupingBy(QuestionAcceptedAnswer::getQuestionId));

        Collection<UUID> matchingSetIds = page.getContent().stream()
                .filter(q -> q.getQuestionType() == QuestionType.MATCHING).map(Question::getQuestionSetId)
                .collect(Collectors.toSet());
        List<QuestionSetOption> entries = matchingSetIds.isEmpty() ? List.of()
                : setOptionRepo.findAllForSets(matchingSetIds);
        Map<UUID, List<QuestionSetOption>> entriesBySet = entries.stream()
                .collect(Collectors.groupingBy(QuestionSetOption::getQuestionSetId));
        Map<UUID, QuestionSetOption> entryById = entries.stream()
                .collect(Collectors.toMap(QuestionSetOption::getId, Function.identity()));
        Map<UUID, List<String>> labelsByQuestion = matchingSetIds.isEmpty() ? Map.of()
                : matchingAnswerRepo.findAllForQuestions(questionIds).stream()
                        .filter(answer -> entryById.containsKey(answer.getQuestionSetOptionId()))
                        .collect(Collectors.groupingBy(QuestionMatchingAnswer::getQuestionId,
                                Collectors.mapping(answer -> entryById.get(answer.getQuestionSetOptionId()).getLabel(),
                                        Collectors.toList())));

        return page.map(q -> QuestionBankItemResult.of(q, optionsByQuestion.getOrDefault(q.getId(), List.of()),
                acceptedByQuestion.getOrDefault(q.getId(), List.of()),
                labelsByQuestion.getOrDefault(q.getId(), List.of()),
                entriesBySet.getOrDefault(q.getQuestionSetId(), List.of())));
    }
}
