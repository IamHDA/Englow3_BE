package com.englow3.quiz.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewOption;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewPair;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion;
import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.result.ContentReviewResult;
import com.englow3.quiz.dto.result.QuizSummaryResult;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizQuestion;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.entity.QuizQuestionOption;
import com.englow3.quiz.entity.QuizQuestionPair;
import com.englow3.quiz.entity.QuizQuestionToken;
import com.englow3.quiz.entity.QuizTokenRole;
import com.englow3.quiz.repository.QuizQuestionOptionRepository;
import com.englow3.quiz.repository.QuizQuestionPairRepository;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizQuestionTokenRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.quiz.service.AdminQuizService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Authoring quizzes. Which payload a question needs depends on its type, and that is checked here rather than in bean
 * validation: the rule is "a REORDER needs a correct order", which an annotation cannot say without restating the type
 * switch in a second place.
 */
@Service
@RequiredArgsConstructor
public class AdminQuizServiceImpl implements AdminQuizService {

    private final QuizRepository quizRepo;
    private final QuizQuestionRepository questionRepo;
    private final QuizQuestionOptionRepository optionRepo;
    private final QuizQuestionTokenRepository tokenRepo;
    private final QuizQuestionPairRepository pairRepo;
    private final UserDirectory userDirectory;

    @Transactional
    public QuizSummaryResult create(CreateQuizCommand command) {
        if (quizRepo.existsBySlug(command.slug())) {
            throw new ConflictException("QUIZ_SLUG_TAKEN", "A quiz already uses the slug %s".formatted(command.slug()));
        }

        Quiz quiz = quizRepo.save(Quiz.draft(command.slug(), command.title(), command.description(), command.category(),
                command.targetLevel(), command.timeLimitSeconds(), command.passingScorePercent(),
                userDirectory.requireCurrentUserId()));
        return summaryOf(quiz);
    }

    /**
     * Appends questions. Not on a live quiz, unlike flashcards: adding a question to one would change what every
     * attempt in flight is being scored out of, and the attempts already recorded would be out of a different total.
     * <p>
     * A rejected quiz is open to questions as well as a draft. It has to be: "add a question about the passive" is the
     * commonest thing a reviewer will ask for, and a gate on DRAFT alone would leave the author unable to do it.
     */
    @Transactional
    public QuizSummaryResult addQuestions(AddQuizQuestionsCommand command) {
        Quiz quiz = requireQuiz(command.quizId());
        if (quiz.getStatus() != QuizStatus.DRAFT && quiz.getStatus() != QuizStatus.REJECTED) {
            throw new ConflictException("QUIZ_NOT_EDITABLE",
                    "Questions can only be added to a draft or rejected quiz; this one is %s"
                            .formatted(quiz.getStatus()));
        }

        int nextOrderNo = Math.toIntExact(questionRepo.countByQuizId(quiz.getId())) + 1;
        List<QuizQuestion> questions = new ArrayList<>();
        List<QuizQuestionOption> options = new ArrayList<>();
        List<QuizQuestionToken> tokens = new ArrayList<>();
        List<QuizQuestionPair> pairs = new ArrayList<>();

        for (NewQuestion source : command.questions()) {
            QuizQuestion question = QuizQuestion.of(quiz.getId(), nextOrderNo++, source.questionType(), source.title(),
                    source.prompt(), source.points(), source.explanation(), source.beforeText(), source.afterText(),
                    source.originalSentence(), source.rewriteKeyword());
            questions.add(question);
            collectPayload(question, source, options, tokens, pairs);
        }

        questionRepo.saveAll(questions);
        optionRepo.saveAll(options);
        tokenRepo.saveAll(tokens);
        pairRepo.saveAll(pairs);

        return summaryOf(quiz);
    }

    /**
     * Every quiz in any status, for the authoring list. The question counts come in one query for the page rather than
     * one per row.
     */
    @Transactional(readOnly = true)
    public Page<ContentReviewResult> searchForAuthoring(QuizStatus status, String title, Pageable pageable) {
        Page<Quiz> page = quizRepo.searchForAuthoring(status, title, pageable);
        Map<UUID, Long> counts = questionRepo.countByQuizIds(page.getContent().stream().map(Quiz::getId).toList());

        return page.map(quiz -> ContentReviewResult.of(quiz, counts.getOrDefault(quiz.getId(), 0L)));
    }

    @Transactional
    public ContentReviewResult publish(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.publish(questionRepo.countByQuizId(quizId), questionRepo.sumPoints(quizId), Instant.now());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult submitForReview(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.submitForReview(questionRepo.countByQuizId(quizId), questionRepo.sumPoints(quizId), Instant.now());
        return reviewStateOf(quiz);
    }

    /** The reviewer's id comes from the token, not the request - nobody credits an approval to someone else. */
    @Transactional
    public ContentReviewResult approve(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.approve(userDirectory.requireCurrentUserId(), questionRepo.countByQuizId(quizId),
                questionRepo.sumPoints(quizId), Instant.now());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult reject(UUID quizId, String note) {
        Quiz quiz = requireQuiz(quizId);
        quiz.reject(userDirectory.requireCurrentUserId(), note, Instant.now());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult archive(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.archive();
        return reviewStateOf(quiz);
    }

    /** The review state is what an authoring action changed, so it is what an authoring action returns. */
    private ContentReviewResult reviewStateOf(Quiz quiz) {
        return ContentReviewResult.of(quiz, questionRepo.countByQuizId(quiz.getId()));
    }

    private void collectPayload(QuizQuestion question, NewQuestion source, List<QuizQuestionOption> options,
            List<QuizQuestionToken> tokens, List<QuizQuestionPair> pairs) {
        switch (source.questionType()) {
            case MULTIPLE_CHOICE -> {
                List<NewOption> given = orEmpty(source.options());
                if (given.stream().noneMatch(NewOption::correct)) {
                    throw new BadRequestException("QUIZ_QUESTION_NO_CORRECT_OPTION",
                            "A multiple choice question needs at least one correct option");
                }
                int orderNo = 1;
                for (NewOption option : given) {
                    options.add(QuizQuestionOption.of(question.getId(), orderNo++, option.label(), option.content(),
                            option.correct()));
                }
            }
            case FILL_BLANK -> addTokens(tokens, question.getId(), QuizTokenRole.ACCEPTED_ANSWER,
                    require(source.acceptedAnswers(), "QUIZ_QUESTION_NO_ACCEPTED_ANSWER",
                            "A fill in the blank question needs at least one accepted answer"));
            case REWRITE -> {
                addTokens(tokens, question.getId(), QuizTokenRole.WORD_BANK, orEmpty(source.wordBank()));
                addTokens(tokens, question.getId(), QuizTokenRole.CORRECT_WORD,
                        require(source.correctWords(), "QUIZ_QUESTION_NO_CORRECT_WORDS",
                                "A rewrite question needs the words of its answer, in order"));
            }
            case REORDER -> {
                addTokens(tokens, question.getId(), QuizTokenRole.SCRAMBLED, orEmpty(source.scrambledWords()));
                addTokens(tokens, question.getId(), QuizTokenRole.CORRECT_ORDER, require(source.correctOrder(),
                        "QUIZ_QUESTION_NO_CORRECT_ORDER", "A reorder question needs its words in the right order"));
            }
            case MATCHING -> {
                List<NewPair> given = require(source.pairs(), "QUIZ_QUESTION_NO_PAIRS",
                        "A matching question needs at least one pair");
                int orderNo = 1;
                for (NewPair pair : given) {
                    pairs.add(QuizQuestionPair.of(question.getId(), orderNo++, pair.leftText(), pair.rightText()));
                }
            }
        }
    }

    private static void addTokens(List<QuizQuestionToken> sink, UUID questionId, QuizTokenRole role,
            List<String> values) {
        int orderNo = 1;
        for (String value : values) {
            sink.add(QuizQuestionToken.of(questionId, role, orderNo++, value));
        }
    }

    private static <T> List<T> require(List<T> values, String code, String message) {
        List<T> given = orEmpty(values);
        if (given.isEmpty()) {
            throw new BadRequestException(code, message);
        }
        return given;
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private QuizSummaryResult summaryOf(Quiz quiz) {
        return QuizSummaryResult.of(quiz, questionRepo.countByQuizId(quiz.getId()), null, 0);
    }

    private Quiz requireQuiz(UUID quizId) {
        return quizRepo.findById(quizId)
                .orElseThrow(() -> new NotFoundException("QUIZ_NOT_FOUND", "No quiz with id %s".formatted(quizId)));
    }
}
