package com.englow3.quiz.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewOption;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewPair;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion;
import com.englow3.quiz.dto.result.QuizSummaryResult;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizQuestion;
import com.englow3.quiz.entity.QuizQuestionOption;
import com.englow3.quiz.entity.QuizQuestionPair;
import com.englow3.quiz.entity.QuizQuestionToken;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.entity.QuizTokenRole;
import com.englow3.quiz.helper.QuizGrader;
import com.englow3.quiz.repository.QuizQuestionOptionRepository;
import com.englow3.quiz.repository.QuizQuestionPairRepository;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizQuestionTokenRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.quiz.service.QuizQuestionAuthoringService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

/** Appends validated question payloads to editable quizzes. */
@Service
@RequiredArgsConstructor
public class QuizQuestionAuthoringServiceImpl implements QuizQuestionAuthoringService {

    private final QuizRepository quizRepo;
    private final QuizQuestionRepository questionRepo;
    private final QuizQuestionOptionRepository optionRepo;
    private final QuizQuestionTokenRepository tokenRepo;
    private final QuizQuestionPairRepository pairRepo;

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
        return QuizSummaryResult.of(quiz, questionRepo.countByQuizId(quiz.getId()), null, 0);
    }

    private void collectPayload(QuizQuestion question, NewQuestion source, List<QuizQuestionOption> options,
            List<QuizQuestionToken> tokens, List<QuizQuestionPair> pairs) {
        switch (source.questionType()) {
            case MULTIPLE_CHOICE -> {
                List<NewOption> given = orEmpty(source.options());
                if (given.size() < 2) {
                    throw new BadRequestException("QUIZ_QUESTION_TOO_FEW_OPTIONS",
                            "A multiple choice question needs at least two options to choose between");
                }
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
                    requireNoBlank(require(source.acceptedAnswers(), "QUIZ_QUESTION_NO_ACCEPTED_ANSWER",
                            "A fill in the blank question needs at least one accepted answer")));
            case REWRITE -> {
                List<String> correctWords = requireNoBlank(
                        require(source.correctWords(), "QUIZ_QUESTION_NO_CORRECT_WORDS",
                                "A rewrite question needs the words of its answer, in order"));
                List<String> wordBank = requireNoBlank(orEmpty(source.wordBank()));
                // The learner builds the answer from the bank, so every word of the answer has to be in it -
                // otherwise the question cannot be got right. An empty bank is dealt from the answer instead.
                if (!wordBank.isEmpty() && !containsAll(wordBank, correctWords)) {
                    throw new BadRequestException("QUIZ_QUESTION_WORD_BANK_INCOMPLETE",
                            "The word bank must contain every word of the answer");
                }
                addTokens(tokens, question.getId(), QuizTokenRole.WORD_BANK, wordBank);
                addTokens(tokens, question.getId(), QuizTokenRole.CORRECT_WORD, correctWords);
            }
            case REORDER -> {
                List<String> correctOrder = requireNoBlank(require(source.correctOrder(),
                        "QUIZ_QUESTION_NO_CORRECT_ORDER", "A reorder question needs its words in the right order"));
                List<String> scrambled = requireNoBlank(orEmpty(source.scrambledWords()));
                // Reordering rearranges; it cannot add or drop a word. Tiles that are not the answer's words would
                // make the right order impossible to build. Left empty, they are dealt from the answer.
                if (!scrambled.isEmpty() && !sameWords(scrambled, correctOrder)) {
                    throw new BadRequestException("QUIZ_QUESTION_SCRAMBLE_MISMATCH",
                            "The scrambled words must be exactly the words of the correct order");
                }
                addTokens(tokens, question.getId(), QuizTokenRole.SCRAMBLED, scrambled);
                addTokens(tokens, question.getId(), QuizTokenRole.CORRECT_ORDER, correctOrder);
            }
            case MATCHING -> {
                List<NewPair> given = require(source.pairs(), "QUIZ_QUESTION_NO_PAIRS",
                        "A matching question needs at least one pair");
                if (given.size() < 2) {
                    throw new BadRequestException("QUIZ_QUESTION_TOO_FEW_PAIRS",
                            "A matching question needs at least two pairs, or there is nothing to match");
                }
                // The learner's answer arrives with its halves joined by this character; a half containing it
                // would be split in two and could never be marked right.
                if (given.stream().anyMatch(pair -> pair.leftText().contains(QuizGrader.MATCHING_SEPARATOR)
                        || pair.rightText().contains(QuizGrader.MATCHING_SEPARATOR))) {
                    throw new BadRequestException("QUIZ_QUESTION_PAIR_HAS_SEPARATOR",
                            "A matching pair cannot contain '%s'".formatted(QuizGrader.MATCHING_SEPARATOR));
                }
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

    private static List<String> requireNoBlank(List<String> values) {
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new BadRequestException("QUIZ_QUESTION_BLANK_ANSWER", "An answer or a tile cannot be blank");
        }
        return values;
    }

    /** Whether {@code bank} holds every word of {@code words}, a repeated word as often as it repeats. */
    private static boolean containsAll(List<String> bank, List<String> words) {
        Map<String, Long> available = counts(bank);
        return counts(words).entrySet().stream()
                .allMatch(entry -> available.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    private static boolean sameWords(List<String> left, List<String> right) {
        return counts(left).equals(counts(right));
    }

    /** Compared the way the grader compares: case and surrounding space do not matter. */
    private static Map<String, Long> counts(List<String> words) {
        return words.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        word -> word.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT),
                        java.util.stream.Collectors.counting()));
    }

    private Quiz requireQuiz(UUID quizId) {
        return quizRepo.findById(quizId)
                .orElseThrow(() -> new NotFoundException("QUIZ_NOT_FOUND", "No quiz with id %s".formatted(quizId)));
    }
}
