package com.englow3.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.quiz.dto.command.SubmitQuizAttemptCommand;
import com.englow3.quiz.dto.command.SubmitQuizAttemptCommand.SubmittedAnswer;
import com.englow3.quiz.dto.result.QuizAttemptResult;
import com.englow3.quiz.dto.result.QuizPaperResult;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizAttempt;
import com.englow3.quiz.entity.QuizQuestion;
import com.englow3.quiz.entity.QuizQuestionOption;
import com.englow3.quiz.entity.QuizQuestionPair;
import com.englow3.quiz.entity.QuizQuestionType;
import com.englow3.quiz.repository.QuizAttemptAnswerRepository;
import com.englow3.quiz.repository.QuizAttemptRepository;
import com.englow3.quiz.repository.QuizQuestionOptionRepository;
import com.englow3.quiz.repository.QuizQuestionPairRepository;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizQuestionTokenRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

/**
 * What this class can get wrong that the grader cannot: handing the answer key to a learner who is still sitting, and
 * reshuffling a matching question under them. Both are asserted here; the marking itself is QuizGraderTest.
 */
class QuizServiceTest {

    private final QuizRepository quizRepo = mock(QuizRepository.class);
    private final QuizQuestionRepository questionRepo = mock(QuizQuestionRepository.class);
    private final QuizQuestionOptionRepository optionRepo = mock(QuizQuestionOptionRepository.class);
    private final QuizQuestionTokenRepository tokenRepo = mock(QuizQuestionTokenRepository.class);
    private final QuizQuestionPairRepository pairRepo = mock(QuizQuestionPairRepository.class);
    private final QuizAttemptRepository attemptRepo = mock(QuizAttemptRepository.class);
    private final QuizAttemptAnswerRepository attemptAnswerRepo = mock(QuizAttemptAnswerRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);

    private final QuizService service = new com.englow3.quiz.service.impl.QuizServiceImpl(quizRepo, questionRepo,
            optionRepo, tokenRepo, pairRepo, attemptRepo, attemptAnswerRepo, userDirectory);

    private final UUID userId = UUID.randomUUID();
    private Quiz quiz;
    private QuizQuestion choice;
    private QuizQuestionOption right;
    private QuizQuestionOption wrong;

    @BeforeEach
    void setUp() {
        quiz = Quiz.draft("present-perfect", "Present perfect", "", "Grammar", "B1", 600, (short) 60,
                UUID.randomUUID());
        quiz.publish(1, 2, Instant.now());

        choice = QuizQuestion.of(quiz.getId(), 1, QuizQuestionType.MULTIPLE_CHOICE, "Pick one", "She ___ here.",
                (short) 2, "Present perfect for unfinished time.", null, null, null, null);
        right = QuizQuestionOption.of(choice.getId(), 1, "A", "has been", true);
        wrong = QuizQuestionOption.of(choice.getId(), 2, "B", "have been", false);

        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(quizRepo.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(questionRepo.findByQuizIdOrderByOrderNo(quiz.getId())).thenReturn(List.of(choice));
        when(optionRepo.findByQuizQuestionIdInOrderByOrderNo(anyCollection())).thenReturn(List.of(right, wrong));
        when(tokenRepo.findByQuizQuestionIdInOrderByOrderNo(anyCollection())).thenReturn(List.of());
        when(pairRepo.findByQuizQuestionIdInOrderByOrderNo(anyCollection())).thenReturn(List.of());
        when(attemptRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private QuizAttempt liveAttempt() {
        QuizAttempt attempt = QuizAttempt.start(quiz, userId, 1, new BigDecimal("2"), Instant.now());
        when(attemptRepo.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptRepo.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        return attempt;
    }

    @Nested
    class DeliveringThePaper {

        /**
         * The whole reason the paper is a separate projection. If an option ever carried its correct flag, the answers
         * would be one network tab away.
         */
        @Test
        void carriesNoCorrectnessFlagOnAnOption() {
            QuizPaperResult paper = service.paperForAttempt(liveAttempt().getId());

            assertThat(paper.questions()).singleElement().satisfies(question -> {
                assertThat(question.options()).extracting("label").containsExactly("A", "B");
                // OptionResult has no `correct` component at all - this asserts the shape, not a false value.
                assertThat(question.options().get(0).getClass().getRecordComponents())
                        .extracting(java.lang.reflect.RecordComponent::getName)
                        .containsExactly("id", "orderNo", "label", "content");
            });
        }

        @Test
        void refusesAnAttemptThatIsNotTheCallersOwn() {
            QuizAttempt someoneElses = QuizAttempt.start(quiz, UUID.randomUUID(), 1, BigDecimal.ONE, Instant.now());
            when(attemptRepo.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

            assertThatThrownBy(() -> service.paperForAttempt(someoneElses.getId()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("code", "QUIZ_ATTEMPT_NOT_FOUND");
        }
    }

    @Nested
    class MatchingQuestions {

        private QuizQuestion matching;

        @BeforeEach
        void useAMatchingQuestion() {
            matching = QuizQuestion.of(quiz.getId(), 1, QuizQuestionType.MATCHING, "Join them", "Match the clauses",
                    (short) 2, "", null, null, null, null);
            when(questionRepo.findByQuizIdOrderByOrderNo(quiz.getId())).thenReturn(List.of(matching));
            when(optionRepo.findByQuizQuestionIdInOrderByOrderNo(anyCollection())).thenReturn(List.of());
            when(pairRepo.findByQuizQuestionIdInOrderByOrderNo(anyCollection()))
                    .thenReturn(List.of(QuizQuestionPair.of(matching.getId(), 1, "We stayed in", "because it rained"),
                            QuizQuestionPair.of(matching.getId(), 2, "The film ended", "so we left"),
                            QuizQuestionPair.of(matching.getId(), 3, "He was tired", "but he kept going")));
        }

        /** Reloading mid-quiz must not deal a new puzzle - the seed is the attempt and the question, not the clock. */
        @Test
        void showsTheSameArrangementOnEveryLoadOfOneAttempt() {
            UUID attemptId = liveAttempt().getId();

            List<String> first = service.paperForAttempt(attemptId).questions().get(0).rightTexts();
            List<String> second = service.paperForAttempt(attemptId).questions().get(0).rightTexts();

            assertThat(first).isEqualTo(second);
        }

        @Test
        void keepsEveryRightHalfWhileShuffling() {
            List<String> rights = service.paperForAttempt(liveAttempt().getId()).questions().get(0).rightTexts();

            assertThat(rights).containsExactlyInAnyOrder("because it rained", "so we left", "but he kept going");
        }

        /** Left halves stay in their stored order - they are the question, not the answer. */
        @Test
        void leavesTheLeftColumnInOrder() {
            List<String> lefts = service.paperForAttempt(liveAttempt().getId()).questions().get(0).leftTexts();

            assertThat(lefts).containsExactly("We stayed in", "The film ended", "He was tired");
        }
    }

    @Nested
    class Submitting {

        @Test
        void scoresACorrectAnswerAndMarksThePass() {
            QuizAttempt attempt = liveAttempt();

            QuizAttemptResult result = service.submit(new SubmitQuizAttemptCommand(attempt.getId(),
                    List.of(new SubmittedAnswer(choice.getId(), right.getId().toString()))));

            assertThat(result.score()).isEqualByComparingTo("2");
            assertThat(result.correctAnswerCount()).isEqualTo(1);
            assertThat(result.passed()).isTrue();
        }

        /** An unanswered question is marked wrong rather than skipped, or the percentage would flatter the learner. */
        @Test
        void marksAQuestionThatWasNeverAnsweredAsWrong() {
            QuizAttempt attempt = liveAttempt();

            QuizAttemptResult result = service.submit(new SubmitQuizAttemptCommand(attempt.getId(), List.of()));

            assertThat(result.score()).isEqualByComparingTo("0");
            assertThat(result.correctAnswerCount()).isZero();
            assertThat(result.passed()).isFalse();
            assertThat(result.reviews()).singleElement().satisfies(review -> assertThat(review.correct()).isFalse());
        }

        /** The review shows the option the learner picked, not the uuid they sent. */
        @Test
        void reportsTheChosenOptionInWordsOnTheReview() {
            QuizAttempt attempt = liveAttempt();

            QuizAttemptResult result = service.submit(new SubmitQuizAttemptCommand(attempt.getId(),
                    List.of(new SubmittedAnswer(choice.getId(), wrong.getId().toString()))));

            assertThat(result.reviews()).singleElement().satisfies(review -> {
                assertThat(review.userAnswerText()).isEqualTo("B. have been");
                assertThat(review.correctAnswerText()).isEqualTo("A. has been");
            });
        }
    }
}
