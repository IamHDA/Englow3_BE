package com.englow3.exam.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Part;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Question;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.QuestionSet;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Section;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.ExamAttemptResult.OptionReviewResult;
import com.englow3.exam.dto.result.ExamAttemptResult.QuestionReviewResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.AttemptAnswer;
import com.englow3.exam.entity.AttemptAnswerOption;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.query.ExamGradingQuery;
import com.englow3.exam.query.ExamGradingQuery.GradingQuestion;
import com.englow3.exam.query.LearnerExamPaperQuery;
import com.englow3.exam.repository.AttemptAnswerOptionRepository;
import com.englow3.exam.repository.AttemptAnswerRepository;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.service.ExamAttemptService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.PlacementRecorder;
import com.englow3.user.api.UserDirectory;

/** Exam attempt lifecycle, paper delivery, grading, and result review. */
@Service
public class ExamAttemptServiceImpl implements ExamAttemptService {

    private final ExamRepository examRepo;
    private final ExamAttemptRepository attemptRepo;
    private final AttemptAnswerRepository answerRepo;
    private final AttemptAnswerOptionRepository answerOptionRepo;
    private final LearnerExamPaperQuery paperQuery;
    private final ExamGradingQuery gradingQuery;
    private final UserDirectory userDirectory;
    private final PlacementRecorder placementRecorder;
    private final PresignedUrlResolver presignedUrls;
    private final Clock clock;
    private final String examBucket;
    private final Duration mediaUrlTtl;

    public ExamAttemptServiceImpl(ExamRepository examRepo, ExamAttemptRepository attemptRepo,
            AttemptAnswerRepository answerRepo, AttemptAnswerOptionRepository answerOptionRepo,
            LearnerExamPaperQuery paperQuery, ExamGradingQuery gradingQuery, UserDirectory userDirectory,
            PlacementRecorder placementRecorder, PresignedUrlResolver presignedUrls, Clock clock,
            @Value("${app.storage.exam-bucket}") String examBucket,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl) {
        this.examRepo = examRepo;
        this.attemptRepo = attemptRepo;
        this.answerRepo = answerRepo;
        this.answerOptionRepo = answerOptionRepo;
        this.paperQuery = paperQuery;
        this.gradingQuery = gradingQuery;
        this.userDirectory = userDirectory;
        this.placementRecorder = placementRecorder;
        this.presignedUrls = presignedUrls;
        this.clock = clock;
        this.examBucket = examBucket;
        this.mediaUrlTtl = mediaUrlTtl;
    }

    @Transactional(readOnly = true)
    public Page<ExamAttemptResult> attemptHistory(Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<ExamAttempt> page = attemptRepo.findByUserIdOrderByStartedAtDesc(userId, pageable);
        Map<UUID, String> titles = page.getContent().isEmpty() ? Map.of()
                : examRepo.findAllById(page.getContent().stream().map(ExamAttempt::getExamId).toList()).stream()
                        .collect(Collectors.toMap(Exam::getId, Exam::getTitle));
        return page.map(attempt -> ExamAttemptResult.summary(attempt, titles.get(attempt.getExamId())));
    }

    @Transactional
    public ExamAttemptResult start(UUID examId) {
        Exam exam = requirePublishedExam(examId);
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = clock.instant();

        var active = attemptRepo.findFirstByUserIdAndExamIdAndStatusOrderByStartedAtDesc(userId, examId,
                ExamAttemptStatus.IN_PROGRESS);
        if (active.isPresent() && now.isBefore(active.get().getExpiresAt())) {
            return ExamAttemptResult.started(active.get(), true);
        }
        active.ifPresent(attempt -> attempt.expire(now));
        if (active.isPresent()) {
            attemptRepo.flush();
        }

        int questionCount = Math.toIntExact(examRepo.countQuestions(examId));
        ExamAttempt attempt = attemptRepo.save(ExamAttempt.start(exam, userId, questionCount, now));
        return ExamAttemptResult.started(attempt, false);
    }

    @Transactional(readOnly = true)
    public LearnerExamPaperResult paperForAttempt(UUID attemptId) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findById(attemptId), attemptId);
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("ATTEMPT_NOT_IN_PROGRESS", "This exam attempt is no longer in progress");
        }
        if (!clock.instant().isBefore(attempt.getExpiresAt())) {
            throw new ConflictException("ATTEMPT_EXPIRED", "This exam attempt has expired");
        }
        return toResult(paperQuery.load(attempt.getExamId()).orElseThrow(() -> examNotFound(attempt.getExamId())));
    }

    @Transactional
    public ExamAttemptResult submit(SubmitExamAttemptCommand command) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findByIdForUpdate(command.attemptId()),
                command.attemptId());
        Instant now = clock.instant();
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("ATTEMPT_ALREADY_FINALIZED", "This exam attempt has already been finalized");
        }
        if (!now.isBefore(attempt.getExpiresAt())) {
            throw new ConflictException("ATTEMPT_EXPIRED", "This exam attempt has expired");
        }

        List<GradingQuestion> questions = gradingQuery.load(attempt.getExamId());
        Map<UUID, GradingQuestion> questionById = questions.stream().collect(
                Collectors.toMap(GradingQuestion::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        validateSubmission(command.answers(), questionById);

        BigDecimal rawScore = BigDecimal.ZERO;
        int correctCount = 0;
        List<AttemptAnswer> storedAnswers = new ArrayList<>();
        List<AttemptAnswerOption> storedOptions = new ArrayList<>();

        for (SubmittedAnswer submitted : command.answers()) {
            GradingQuestion question = questionById.get(submitted.questionId());
            Set<UUID> selectedIds = new HashSet<>(submitted.selectedOptionIds());
            Set<UUID> correctIds = question.options().stream().filter(option -> option.correct())
                    .map(option -> option.id()).collect(Collectors.toSet());
            boolean correct = !correctIds.isEmpty() && selectedIds.equals(correctIds);
            BigDecimal awarded = correct ? question.maxRawScore() : BigDecimal.ZERO;
            if (correct) {
                rawScore = rawScore.add(awarded);
                correctCount++;
            }

            AttemptAnswer answer = AttemptAnswer.graded(attempt.getId(), question.id(), correct, awarded, now);
            storedAnswers.add(answer);
            submitted.selectedOptionIds()
                    .forEach(optionId -> storedOptions.add(AttemptAnswerOption.selected(answer.getId(), optionId)));
        }

        answerRepo.saveAll(storedAnswers);
        answerOptionRepo.saveAll(storedOptions);
        attempt.score(rawScore, correctCount, now);

        Exam exam = examRepo.findById(attempt.getExamId()).orElseThrow(() -> examNotFound(attempt.getExamId()));
        if (exam.getExamType() == ExamType.PLACEMENT) {
            placementRecorder.record(attempt.getUserId(), attempt.getId(), attempt.getScorePercentage());
        }

        return ExamAttemptResult.scored(attempt, buildReview(questions, storedAnswers, storedOptions));
    }

    @Transactional(readOnly = true)
    public ExamAttemptResult result(UUID attemptId) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findById(attemptId), attemptId);
        if (attempt.getStatus() != ExamAttemptStatus.SCORED) {
            throw new ConflictException("ATTEMPT_RESULT_NOT_READY", "This exam attempt has not been scored yet");
        }
        List<AttemptAnswer> answers = answerRepo.findByExamAttemptId(attemptId);
        List<UUID> answerIds = answers.stream().map(AttemptAnswer::getId).toList();
        List<AttemptAnswerOption> selectedOptions = answerIds.isEmpty() ? List.of()
                : answerOptionRepo.findByAttemptAnswerIdIn(answerIds);
        return ExamAttemptResult.scored(attempt,
                buildReview(gradingQuery.load(attempt.getExamId()), answers, selectedOptions));
    }

    private LearnerExamPaperResult toResult(LearnerExamPaperProjection paper) {
        return LearnerExamPaperResult.of(paper.exam(), paper.sections().stream().map(this::toSection).toList());
    }

    private LearnerExamPaperResult.LearnerSection toSection(Section section) {
        return new LearnerExamPaperResult.LearnerSection(section.id(), section.sectionType(), section.orderNo(),
                section.maxRawScore(), section.scoredByCriteria(), section.timeLimitSeconds(),
                section.parts().stream().map(this::toPart).toList());
    }

    private LearnerExamPaperResult.LearnerPart toPart(Part part) {
        return new LearnerExamPaperResult.LearnerPart(part.id(), part.orderNo(), part.title(), part.instruction(),
                part.content(), presignedUrls.resolve(examBucket, part.audioObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(examBucket, part.imageObjectKey(), mediaUrlTtl),
                part.questionSets().stream().map(this::toQuestionSet).toList());
    }

    private LearnerExamPaperResult.QuestionSetResult toQuestionSet(QuestionSet questionSet) {
        return new LearnerExamPaperResult.QuestionSetResult(questionSet.id(), questionSet.title(),
                questionSet.instruction(), questionSet.orderNo(), questionSet.content(),
                presignedUrls.resolve(examBucket, questionSet.audioObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(examBucket, questionSet.imageObjectKey(), mediaUrlTtl),
                questionSet.questions().stream().map(this::toQuestion).toList());
    }

    private LearnerExamPaperResult.QuestionResult toQuestion(Question question) {
        return new LearnerExamPaperResult.QuestionResult(question.id(), question.questionType(), question.content(),
                question.difficultyLevel(), question.skillType(), question.questionCategory(), question.orderNo(),
                question.maxRawScore(),
                question.options().stream().map(option -> new LearnerExamPaperResult.QuestionOptionResult(option.id(),
                        option.content(), option.orderNo())).toList());
    }

    private void validateSubmission(List<SubmittedAnswer> submittedAnswers, Map<UUID, GradingQuestion> questionById) {
        Set<UUID> submittedQuestionIds = new HashSet<>();
        for (SubmittedAnswer answer : submittedAnswers) {
            if (!submittedQuestionIds.add(answer.questionId())) {
                throw new BadRequestException("DUPLICATE_QUESTION_ANSWER",
                        "Question %s was submitted more than once".formatted(answer.questionId()));
            }
            GradingQuestion question = questionById.get(answer.questionId());
            if (question == null) {
                throw new BadRequestException("QUESTION_NOT_IN_EXAM",
                        "Question %s does not belong to this exam".formatted(answer.questionId()));
            }
            Set<UUID> selected = new HashSet<>(answer.selectedOptionIds());
            if (selected.size() != answer.selectedOptionIds().size()) {
                throw new BadRequestException("DUPLICATE_SELECTED_OPTION",
                        "An option was selected more than once for question %s".formatted(answer.questionId()));
            }
            Set<UUID> allowed = question.options().stream().map(option -> option.id()).collect(Collectors.toSet());
            if (!allowed.containsAll(selected)) {
                throw new BadRequestException("OPTION_NOT_IN_QUESTION",
                        "A selected option does not belong to question %s".formatted(answer.questionId()));
            }
            if (question.questionType() == QuestionType.SINGLE_CHOICE && selected.size() > 1) {
                throw new BadRequestException("TOO_MANY_SELECTED_OPTIONS",
                        "Question %s accepts only one option".formatted(answer.questionId()));
            }
        }
    }

    private List<QuestionReviewResult> buildReview(List<GradingQuestion> questions, List<AttemptAnswer> answers,
            List<AttemptAnswerOption> selectedOptions) {
        Map<UUID, AttemptAnswer> answerByQuestion = answers.stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));
        Map<UUID, List<UUID>> optionsByAnswer = new HashMap<>();
        if (!answers.isEmpty()) {
            Map<UUID, UUID> answerQuestionIds = answers.stream()
                    .collect(Collectors.toMap(AttemptAnswer::getId, AttemptAnswer::getQuestionId));
            for (AttemptAnswerOption option : selectedOptions) {
                UUID answerId = option.getAttemptAnswerId();
                optionsByAnswer.computeIfAbsent(answerQuestionIds.get(answerId), ignored -> new ArrayList<>())
                        .add(option.getQuestionOptionId());
            }
        }

        return questions.stream().map(question -> {
            AttemptAnswer answer = answerByQuestion.get(question.id());
            List<UUID> correctIds = question.options().stream().filter(option -> option.correct())
                    .map(option -> option.id()).toList();
            List<OptionReviewResult> options = question.options().stream()
                    .map(option -> new OptionReviewResult(option.id(), option.correct(), option.explanation()))
                    .toList();
            return new QuestionReviewResult(question.id(), optionsByAnswer.getOrDefault(question.id(), List.of()),
                    correctIds, answer != null && answer.isCorrect(),
                    answer == null ? BigDecimal.ZERO : answer.getAwardedRawScore(), question.explanation(), options);
        }).toList();
    }

    private Exam requirePublishedExam(UUID examId) {
        return examRepo.findById(examId).filter(exam -> exam.getStatus() == ExamStatus.PUBLISHED)
                .orElseThrow(() -> examNotFound(examId));
    }

    private ExamAttempt requireOwnedAttempt(java.util.Optional<ExamAttempt> candidate, UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        return candidate.filter(attempt -> attempt.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("EXAM_ATTEMPT_NOT_FOUND",
                        "No exam attempt with id %s".formatted(attemptId)));
    }

    private static NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No published exam with id %s".formatted(examId));
    }
}
