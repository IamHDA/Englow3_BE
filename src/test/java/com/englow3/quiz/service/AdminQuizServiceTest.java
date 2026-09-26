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

/**
 * Which payload a question type requires is the rule worth pinning down here - bean validation cannot express it, so
 * this is the only place that says a REORDER needs its correct order.
 */
class AdminQuizServiceTest {

    private final QuizRepository quizRepo = mock(QuizRepository.class);
    private final QuizQuestionRepository questionRepo = mock(QuizQuestionRepository.class);
    private final QuizQuestionOptionRepository optionRepo = mock(QuizQuestionOptionRepository.class);
    private final QuizQuestionTokenRepository tokenRepo = mock(QuizQuestionTokenRepository.class);
    private final QuizQuestionPairRepository pairRepo = mock(QuizQuestionPairRepository.class);
    private final com.englow3.user.api.UserDirectory userDirectory = mock(com.englow3.user.api.UserDirectory.class);

    private final AdminQuizService service = new com.englow3.quiz.service.impl.AdminQuizServiceImpl(quizRepo,
            questionRepo, optionRepo, tokenRepo, pairRepo, userDirectory);

    private Quiz quiz;

    @BeforeEach
    void setUp() {
        quiz = Quiz.draft("present-perfect", "Present perfect", "", "Grammar", "B1", 600, (short) 60,
                UUID.randomUUID());
        when(quizRepo.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(quizRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionRepo.countByQuizId(quiz.getId())).thenReturn(0L);
        when(userDirectory.requireCurrentUserId()).thenReturn(UUID.randomUUID());
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

        /**
         * Unlike a flashcard set, a live quiz cannot grow. A new question would change what every attempt in flight is
         * scored out of, and leave the finished ones measured against a different total.
         */
        @Test
        void refusesToAddQuestionsToAPublishedQuiz() {
            quiz.publish(1, 1, Instant.now());

            assertThatThrownBy(() -> add(
                    question(QuizQuestionType.MATCHING, null, null, null, null, List.of(new NewPair("a", "b")))))
                            .isInstanceOf(ConflictException.class)
                            .hasFieldOrPropertyWithValue("code", "QUIZ_NOT_EDITABLE");
        }

        /**
         * "Add a question about the passive" is the commonest thing a reviewer will ask for, so a quiz that came back
         * has to accept one. A gate on DRAFT alone would leave the author unable to answer the note.
         */
        @Test
        void acceptsQuestionsOnAQuizThatCameBackFromReview() {
            quiz.submitForReview(1, 1, Instant.now());
            quiz.reject(UUID.randomUUID(), "Add a question about the passive.", Instant.now());

            add(question(QuizQuestionType.MATCHING, null, null, null, null, List.of(new NewPair("a", "b"))));

            verify(questionRepo).saveAll(any());
        }

        @Test
        void refusesToPublishAQuizWhoseQuestionsAreAllWorthNothing() {
            when(questionRepo.countByQuizId(quiz.getId())).thenReturn(3L);
            when(questionRepo.sumPoints(quiz.getId())).thenReturn(0L);

            assertThatThrownBy(() -> service.publish(quiz.getId())).isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_ZERO_POINTS");
        }
    }
}
