package com.englow3.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewOption;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewPair;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizQuestionToken;
import com.englow3.quiz.entity.QuizQuestionType;
import com.englow3.quiz.entity.QuizTokenRole;
import com.englow3.quiz.repository.QuizQuestionOptionRepository;
import com.englow3.quiz.repository.QuizQuestionPairRepository;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizQuestionTokenRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class QuizQuestionAuthoringServiceTest {

    private final QuizRepository quizRepo = mock(QuizRepository.class);
    private final QuizQuestionRepository questionRepo = mock(QuizQuestionRepository.class);
    private final QuizQuestionOptionRepository optionRepo = mock(QuizQuestionOptionRepository.class);
    private final QuizQuestionTokenRepository tokenRepo = mock(QuizQuestionTokenRepository.class);
    private final QuizQuestionPairRepository pairRepo = mock(QuizQuestionPairRepository.class);
    private final QuizQuestionAuthoringService service = new com.englow3.quiz.service.impl.QuizQuestionAuthoringServiceImpl(
            quizRepo, questionRepo, optionRepo, tokenRepo, pairRepo);

    private Quiz quiz;

    @BeforeEach
    void setUp() {
        quiz = Quiz.draft("present-perfect", "Present perfect", "", "Grammar", "B1", 600, (short) 60,
                UUID.randomUUID());
        when(quizRepo.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(questionRepo.countByQuizId(quiz.getId())).thenReturn(0L);
    }

    private NewQuestion question(QuizQuestionType type, List<NewOption> options, List<String> accepted,
            List<String> correctWords, List<String> correctOrder, List<NewPair> pairs) {
        return new NewQuestion(type, "Title", "Prompt", (short) 1, "", null, null, null, null, options, accepted, null,
                correctWords, null, correctOrder, pairs);
    }

    private void add(NewQuestion question) {
        service.addQuestions(new AddQuizQuestionsCommand(quiz.getId(), List.of(question)));
    }

    @Nested
    class RequiredPayload {

        @Test
        void refusesAMultipleChoiceWithNoCorrectOption() {
            assertThatThrownBy(() -> add(question(QuizQuestionType.MULTIPLE_CHOICE,
                    List.of(new NewOption("A", "one", false), new NewOption("B", "two", false)), null, null, null,
                    null))).isInstanceOf(BadRequestException.class).hasFieldOrPropertyWithValue("code",
                            "QUIZ_QUESTION_NO_CORRECT_OPTION");
        }

        @Test
        void refusesAFillBlankWithNoAcceptedAnswer() {
            assertThatThrownBy(() -> add(question(QuizQuestionType.FILL_BLANK, null, List.of(), null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_NO_ACCEPTED_ANSWER");
        }

        @Test
        void refusesAReorderWithNoCorrectOrder() {
            assertThatThrownBy(() -> add(question(QuizQuestionType.REORDER, null, null, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_NO_CORRECT_ORDER");
        }

        @Test
        void refusesAMultipleChoiceWithOneOption() {
            assertThatThrownBy(() -> add(question(QuizQuestionType.MULTIPLE_CHOICE,
                    List.of(new NewOption("A", "The only one", true)), null, null, null, null)))
                            .isInstanceOf(BadRequestException.class)
                            .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_TOO_FEW_OPTIONS");
        }

        /** Tiles that are not the answer's words make the right order impossible to build. */
        @Test
        void refusesScrambledWordsThatAreNotTheAnswersWords() {
            NewQuestion reorder = new NewQuestion(QuizQuestionType.REORDER, "Title", "Prompt", (short) 1, "", null,
                    null, null, null, null, null, null, null, List.of("She", "left"), List.of("She", "has", "left"),
                    null);

            assertThatThrownBy(() -> add(reorder)).isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_SCRAMBLE_MISMATCH");
        }

        @Test
        void refusesAWordBankMissingAWordOfTheAnswer() {
            NewQuestion rewrite = new NewQuestion(QuizQuestionType.REWRITE, "Title", "Prompt", (short) 1, "", null,
                    null, null, null, null, null, List.of("She", "left"), List.of("She", "has", "left"), null, null,
                    null);

            assertThatThrownBy(() -> add(rewrite)).isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_WORD_BANK_INCOMPLETE");
        }

        /** The answer's halves arrive joined by '|'; a half containing it could never be marked right. */
        @Test
        void refusesAMatchingPairContainingTheSeparator() {
            assertThatThrownBy(() -> add(question(QuizQuestionType.MATCHING, null, null, null, null,
                    List.of(new NewPair("either|or", "b"), new NewPair("c", "d")))))
                            .isInstanceOf(BadRequestException.class)
                            .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_PAIR_HAS_SEPARATOR");
        }

        @Test
        void refusesAMatchingWithNoPairs() {
            assertThatThrownBy(() -> add(question(QuizQuestionType.MATCHING, null, null, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_QUESTION_NO_PAIRS");
        }
    }

    @Nested
    class StoringPayload {

        @SuppressWarnings("unchecked")
        @Test
        void keepsTheCorrectOrderAsAnOrderedList() {
            add(question(QuizQuestionType.REORDER, null, null, null, List.of("She", "has", "left"), null));

            ArgumentCaptor<List<QuizQuestionToken>> captor = ArgumentCaptor.forClass(List.class);
            verify(tokenRepo).saveAll(captor.capture());

            assertThat(captor.getValue())
                    .extracting(QuizQuestionToken::getRole, QuizQuestionToken::getOrderNo, QuizQuestionToken::getValue)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(QuizTokenRole.CORRECT_ORDER, 1, "She"),
                            org.assertj.core.groups.Tuple.tuple(QuizTokenRole.CORRECT_ORDER, 2, "has"),
                            org.assertj.core.groups.Tuple.tuple(QuizTokenRole.CORRECT_ORDER, 3, "left"));
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void refusesToAddQuestionsToAPublishedQuiz() {
            quiz.publish(1, 1, Instant.now());

            assertThatThrownBy(() -> add(question(QuizQuestionType.MATCHING, null, null, null, null,
                    List.of(new NewPair("a", "b"), new NewPair("c", "d"))))).isInstanceOf(ConflictException.class)
                            .hasFieldOrPropertyWithValue("code", "QUIZ_NOT_EDITABLE");
        }

        @Test
        void acceptsQuestionsOnAQuizThatCameBackFromReview() {
            quiz.submitForReview(1, 1, Instant.now());
            quiz.reject(UUID.randomUUID(), "Add a question about the passive.", Instant.now());

            add(question(QuizQuestionType.MATCHING, null, null, null, null,
                    List.of(new NewPair("a", "b"), new NewPair("c", "d"))));

            verify(questionRepo).saveAll(any());
        }
    }
}
