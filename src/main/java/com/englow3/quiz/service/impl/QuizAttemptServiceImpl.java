package com.englow3.quiz.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.quiz.dto.command.SubmitQuizAttemptCommand;
import com.englow3.quiz.dto.command.SubmitQuizAttemptCommand.SubmittedAnswer;
import com.englow3.quiz.dto.result.QuizAttemptResult;
import com.englow3.quiz.dto.result.QuizAttemptResult.QuestionReviewResult;
import com.englow3.quiz.dto.result.QuizPaperResult;
import com.englow3.quiz.dto.result.QuizQuestionResult;
import com.englow3.quiz.dto.result.QuizQuestionResult.OptionResult;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizAttempt;
import com.englow3.quiz.entity.QuizAttemptAnswer;
import com.englow3.quiz.entity.QuizAttemptStatus;
import com.englow3.quiz.entity.QuizQuestion;
import com.englow3.quiz.entity.QuizQuestionOption;
import com.englow3.quiz.entity.QuizQuestionPair;
import com.englow3.quiz.entity.QuizQuestionToken;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.entity.QuizTokenRole;
import com.englow3.quiz.helper.QuizGrader;
import com.englow3.quiz.helper.QuizGrader.GradableQuestion;
import com.englow3.quiz.repository.QuizAttemptAnswerRepository;
import com.englow3.quiz.repository.QuizAttemptRepository;
import com.englow3.quiz.repository.QuizQuestionOptionRepository;
import com.englow3.quiz.repository.QuizQuestionPairRepository;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizQuestionTokenRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.quiz.service.QuizAttemptService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Sitting and marking a quiz. */
@Service
@RequiredArgsConstructor
public class QuizAttemptServiceImpl implements QuizAttemptService {

    private final QuizRepository quizRepo;
    private final QuizQuestionRepository questionRepo;
    private final QuizQuestionOptionRepository optionRepo;
    private final QuizQuestionTokenRepository tokenRepo;
    private final QuizQuestionPairRepository pairRepo;
    private final QuizAttemptRepository attemptRepo;
    private final QuizAttemptAnswerRepository attemptAnswerRepo;
    private final UserDirectory userDirectory;

    @Transactional
    public QuizAttemptResult start(UUID quizId) {
        Quiz quiz = requirePublished(quizId);
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = Instant.now();

        var active = attemptRepo.findFirstByUserIdAndQuizIdAndStatusOrderByStartedAtDesc(userId, quizId,
                QuizAttemptStatus.IN_PROGRESS);
        if (active.isPresent() && now.isBefore(active.get().getExpiresAt())) {
            return QuizAttemptResult.started(active.get(), quiz.getTitle(), true);
        }
        active.ifPresent(QuizAttempt::expire);
        if (active.isPresent()) {
            attemptRepo.flush();
        }

        long questionCount = questionRepo.countByQuizId(quizId);
        BigDecimal maxScore = BigDecimal.valueOf(questionRepo.sumPoints(quizId));
        QuizAttempt attempt = attemptRepo
                .save(QuizAttempt.start(quiz, userId, Math.toIntExact(questionCount), maxScore, now));

        return QuizAttemptResult.started(attempt, quiz.getTitle(), false);
    }

    @Transactional(readOnly = true)
    public QuizPaperResult paperForAttempt(UUID attemptId) {
        QuizAttempt attempt = requireOwnedAttempt(attemptId);
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("QUIZ_ATTEMPT_NOT_IN_PROGRESS", "This quiz attempt is no longer in progress");
        }
        if (!Instant.now().isBefore(attempt.getExpiresAt())) {
            throw new ConflictException("QUIZ_ATTEMPT_EXPIRED", "This quiz attempt has expired");
        }

        Quiz quiz = requirePublished(attempt.getQuizId());
        List<QuizQuestion> questions = questionRepo.findByQuizIdOrderByOrderNo(quiz.getId());
        QuestionContent content = loadContent(questions);
        List<QuizQuestionResult> results = questions.stream()
                .map(question -> toLearnerQuestion(question, content, attempt.getId())).toList();

        return new QuizPaperResult(attempt.getId(), quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                quiz.getTimeLimitSeconds(), attempt.getExpiresAt(), results);
    }

    @Transactional
    public QuizAttemptResult submit(SubmitQuizAttemptCommand command) {
        QuizAttempt attempt = attemptRepo.findByIdForUpdate(command.attemptId())
                .filter(candidate -> candidate.getUserId().equals(userDirectory.requireCurrentUserId()))
                .orElseThrow(() -> attemptNotFound(command.attemptId()));
        Instant now = Instant.now();

        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("QUIZ_ATTEMPT_ALREADY_FINALIZED",
                    "This quiz attempt has already been finalized");
        }
        if (!now.isBefore(attempt.getExpiresAt())) {
            throw new ConflictException("QUIZ_ATTEMPT_EXPIRED", "This quiz attempt has expired");
        }

        Quiz quiz = requirePublished(attempt.getQuizId());
        List<QuizQuestion> questions = questionRepo.findByQuizIdOrderByOrderNo(quiz.getId());
        QuestionContent content = loadContent(questions);
        Map<UUID, String> responses = command.answers().stream().collect(
                Collectors.toMap(SubmittedAnswer::questionId, answer -> answer.response(), (left, right) -> left));

        BigDecimal score = BigDecimal.ZERO;
        int correctCount = 0;
        List<QuizAttemptAnswer> storedAnswers = new ArrayList<>();
        List<QuestionReviewResult> reviews = new ArrayList<>();

        for (QuizQuestion question : questions) {
            String response = responses.getOrDefault(question.getId(), "");
            GradableQuestion gradable = content.gradableFor(question);
            boolean correct = QuizGrader.isCorrect(gradable, response);
            BigDecimal awarded = correct ? BigDecimal.valueOf(question.getPoints()) : BigDecimal.ZERO;

            if (correct) {
                score = score.add(awarded);
                correctCount++;
            }

            storedAnswers.add(QuizAttemptAnswer.graded(attempt.getId(), question.getId(), response, correct, awarded));
            reviews.add(new QuestionReviewResult(question.getId(), question.getQuestionType(), question.getPrompt(),
                    content.userAnswerText(question, response),
                    QuizGrader.correctAnswerText(gradable, content.correctOptionLabels(question)), correct, awarded,
                    question.getPoints(), question.getExplanation()));
        }

        attemptAnswerRepo.saveAll(storedAnswers);
        attempt.score(score, correctCount, quiz.getPassingScorePercent(), now);
        return QuizAttemptResult.scored(attempt, quiz.getTitle(), reviews);
    }

    @Transactional(readOnly = true)
    public QuizAttemptResult result(UUID attemptId) {
        QuizAttempt attempt = requireOwnedAttempt(attemptId);
        if (attempt.getStatus() != QuizAttemptStatus.SCORED) {
            throw new ConflictException("QUIZ_ATTEMPT_NOT_SCORED", "This quiz attempt has not been scored");
        }

        Quiz quiz = requirePublished(attempt.getQuizId());
        List<QuizQuestion> questions = questionRepo.findByQuizIdOrderByOrderNo(quiz.getId());
        QuestionContent content = loadContent(questions);
        Map<UUID, QuizAttemptAnswer> answers = attemptAnswerRepo.findByQuizAttemptId(attemptId).stream()
                .collect(Collectors.toMap(QuizAttemptAnswer::getQuizQuestionId, answer -> answer));

        List<QuestionReviewResult> reviews = questions.stream().map(question -> {
            QuizAttemptAnswer answer = answers.get(question.getId());
            String response = answer == null ? "" : answer.getResponse();
            return new QuestionReviewResult(question.getId(), question.getQuestionType(), question.getPrompt(),
                    content.userAnswerText(question, response),
                    QuizGrader.correctAnswerText(content.gradableFor(question), content.correctOptionLabels(question)),
                    answer != null && answer.isCorrect(), answer == null ? BigDecimal.ZERO : answer.getAwardedPoints(),
                    question.getPoints(), question.getExplanation());
        }).toList();

        return QuizAttemptResult.scored(attempt, quiz.getTitle(), reviews);
    }

    private QuizQuestionResult toLearnerQuestion(QuizQuestion question, QuestionContent content, UUID attemptId) {
        List<OptionResult> options = content.optionsOf(question).stream().map(
                option -> new OptionResult(option.getId(), option.getOrderNo(), option.getLabel(), option.getContent()))
                .toList();
        List<QuizQuestionPair> pairs = content.pairsOf(question);

        return new QuizQuestionResult(question.getId(), question.getOrderNo(), question.getQuestionType(),
                question.getTitle(), question.getPrompt(), question.getPoints(), question.getBeforeText(),
                question.getAfterText(), question.getOriginalSentence(), question.getRewriteKeyword(), options,
                content.tokensOf(question, QuizTokenRole.WORD_BANK),
                content.tokensOf(question, QuizTokenRole.SCRAMBLED),
                pairs.stream().map(QuizQuestionPair::getLeftText).toList(), shuffledRights(pairs, attemptId, question));
    }

    private List<String> shuffledRights(List<QuizQuestionPair> pairs, UUID attemptId, QuizQuestion question) {
        List<String> rights = new ArrayList<>(pairs.stream().map(QuizQuestionPair::getRightText).toList());
        java.util.Collections.shuffle(rights,
                new Random(attemptId.getMostSignificantBits() ^ question.getId().getMostSignificantBits()));
        return rights;
    }

    private QuestionContent loadContent(List<QuizQuestion> questions) {
        List<UUID> ids = questions.stream().map(QuizQuestion::getId).toList();
        if (ids.isEmpty()) {
            return new QuestionContent(Map.of(), Map.of(), Map.of());
        }
        return new QuestionContent(
                groupBy(optionRepo.findByQuizQuestionIdInOrderByOrderNo(ids), QuizQuestionOption::getQuizQuestionId),
                groupBy(tokenRepo.findByQuizQuestionIdInOrderByOrderNo(ids), QuizQuestionToken::getQuizQuestionId),
                groupBy(pairRepo.findByQuizQuestionIdInOrderByOrderNo(ids), QuizQuestionPair::getQuizQuestionId));
    }

    private static <T> Map<UUID, List<T>> groupBy(Collection<T> items, java.util.function.Function<T, UUID> key) {
        Map<UUID, List<T>> grouped = new HashMap<>();
        items.forEach(item -> grouped.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>()).add(item));
        return grouped;
    }

    private Quiz requirePublished(UUID quizId) {
        return quizRepo.findById(quizId).filter(quiz -> quiz.getStatus() == QuizStatus.PUBLISHED).orElseThrow(
                () -> new NotFoundException("QUIZ_NOT_FOUND", "No published quiz with id %s".formatted(quizId)));
    }

    private QuizAttempt requireOwnedAttempt(UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        return attemptRepo.findById(attemptId).filter(attempt -> attempt.getUserId().equals(userId))
                .orElseThrow(() -> attemptNotFound(attemptId));
    }

    private static NotFoundException attemptNotFound(UUID attemptId) {
        return new NotFoundException("QUIZ_ATTEMPT_NOT_FOUND", "No quiz attempt with id %s".formatted(attemptId));
    }

    private record QuestionContent(Map<UUID, List<QuizQuestionOption>> options,
            Map<UUID, List<QuizQuestionToken>> tokens, Map<UUID, List<QuizQuestionPair>> pairs) {

        List<QuizQuestionOption> optionsOf(QuizQuestion question) {
            return options.getOrDefault(question.getId(), List.of());
        }

        List<QuizQuestionPair> pairsOf(QuizQuestion question) {
            return pairs.getOrDefault(question.getId(), List.of());
        }

        List<String> tokensOf(QuizQuestion question, QuizTokenRole role) {
            return tokens.getOrDefault(question.getId(), List.of()).stream().filter(token -> token.getRole() == role)
                    .map(QuizQuestionToken::getValue).toList();
        }

        GradableQuestion gradableFor(QuizQuestion question) {
            List<String> correctWords = tokensOf(question, QuizTokenRole.CORRECT_WORD);
            if (correctWords.isEmpty()) {
                correctWords = tokensOf(question, QuizTokenRole.CORRECT_ORDER);
            }
            return new GradableQuestion(question.getQuestionType(),
                    optionsOf(question).stream().filter(QuizQuestionOption::isCorrect)
                            .map(option -> option.getId().toString()).toList(),
                    tokensOf(question, QuizTokenRole.ACCEPTED_ANSWER), correctWords,
                    pairsOf(question).stream().map(QuizQuestionPair::getRightText).toList());
        }

        List<String> correctOptionLabels(QuizQuestion question) {
            return optionsOf(question).stream().filter(QuizQuestionOption::isCorrect)
                    .map(option -> "%s. %s".formatted(option.getLabel(), option.getContent())).toList();
        }

        String userAnswerText(QuizQuestion question, String response) {
            if (question.getQuestionType() != com.englow3.quiz.entity.QuizQuestionType.MULTIPLE_CHOICE) {
                return response;
            }
            return optionsOf(question).stream().filter(option -> option.getId().toString().equals(response))
                    .map(option -> "%s. %s".formatted(option.getLabel(), option.getContent())).findFirst().orElse("");
        }
    }
}
