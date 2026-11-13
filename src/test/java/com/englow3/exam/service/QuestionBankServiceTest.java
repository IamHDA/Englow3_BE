package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.englow3.exam.dto.command.SearchQuestionBankCommand;
import com.englow3.exam.dto.result.QuestionBankItemResult;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionAcceptedAnswer;
import com.englow3.exam.entity.QuestionMatchingAnswer;
import com.englow3.exam.entity.QuestionSetOption;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SkillType;
import com.englow3.exam.repository.QuestionAcceptedAnswerRepository;
import com.englow3.exam.repository.QuestionMatchingAnswerRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetOptionRepository;
import com.englow3.exam.service.impl.QuestionBankServiceImpl;

class QuestionBankServiceTest {

    private final QuestionRepository questionRepo = mock(QuestionRepository.class);
    private final QuestionOptionRepository optionRepo = mock(QuestionOptionRepository.class);
    private final QuestionAcceptedAnswerRepository acceptedRepo = mock(QuestionAcceptedAnswerRepository.class);
    private final QuestionMatchingAnswerRepository matchingRepo = mock(QuestionMatchingAnswerRepository.class);
    private final QuestionSetOptionRepository setOptionRepo = mock(QuestionSetOptionRepository.class);
    private final QuestionBankService service = new QuestionBankServiceImpl(questionRepo, optionRepo, acceptedRepo,
            matchingRepo, setOptionRepo);

    private static Question question(UUID setId, QuestionType type) {
        return Question.create(setId, type, "Question", DifficultyLevel.MEDIUM, SkillType.READING, null, 1,
                BigDecimal.ONE, null, null);
    }

    private List<QuestionBankItemResult> search(Question... questions) {
        when(questionRepo.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(questions), PageRequest.of(0, 20), questions.length));
        return service.searchQuestionBank(new SearchQuestionBankCommand(null, null, null), PageRequest.of(0, 20))
                .getContent();
    }

    @Test
    void aGapFillQuestionComesWithEverySpellingItAccepts() {
        Question gap = question(UUID.randomUUID(), QuestionType.TEXT_ENTRY);
        when(acceptedRepo.findAllForQuestions(List.of(gap.getId())))
                .thenReturn(List.of(QuestionAcceptedAnswer.create(gap.getId(), "seven", false, 1),
                        QuestionAcceptedAnswer.create(gap.getId(), "7", false, 2)));

        QuestionBankItemResult item = search(gap).get(0);

        assertThat(item.acceptedAnswers()).extracting(QuestionBankItemResult.AcceptedAnswer::text)
                .containsExactly("seven", "7");
        assertThat(item.setOptions()).isEmpty();
        verify(setOptionRepo, never()).findAllForSets(any());
    }

    @Test
    void aMatchingQuestionComesWithItsAnswerLabelAndTheWholeListItChoosesFrom() {
        UUID setId = UUID.randomUUID();
        Question match = question(setId, QuestionType.MATCHING);
        QuestionSetOption first = QuestionSetOption.create(setId, "i", "Heading one", 1);
        QuestionSetOption second = QuestionSetOption.create(setId, "ii", "Heading two", 2);
        when(setOptionRepo.findAllForSets(any())).thenReturn(List.of(first, second));
        when(matchingRepo.findAllForQuestions(List.of(match.getId())))
                .thenReturn(List.of(QuestionMatchingAnswer.of(match.getId(), second.getId())));

        QuestionBankItemResult item = search(match).get(0);

        assertThat(item.matchingLabels()).containsExactly("ii");
        assertThat(item.setOptions()).extracting(QuestionBankItemResult.SetOption::label).containsExactly("i", "ii");
    }

    @Test
    void aChoiceQuestionCarriesNoListEvenWhenItsSetHasOne() {
        UUID setId = UUID.randomUUID();
        Question match = question(setId, QuestionType.MATCHING);
        Question choice = question(setId, QuestionType.SINGLE_CHOICE);
        when(setOptionRepo.findAllForSets(any())).thenReturn(List.of(QuestionSetOption.create(setId, "A", "x", 1)));

        List<QuestionBankItemResult> items = search(match, choice);

        assertThat(items.get(1).setOptions()).isEmpty();
        assertThat(items.get(1).matchingLabels()).isEmpty();
    }
}
