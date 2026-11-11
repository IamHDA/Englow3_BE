package com.englow3.exam.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.api.ExamSittingScored;
import com.englow3.exam.dto.command.SaveExamDraftCommand;
import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Part;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Question;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.QuestionSet;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Section;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection;
import com.englow3.exam.dto.result.ExamAttemptResult.OptionReviewResult;
import com.englow3.exam.dto.result.ExamAttemptResult.QuestionReviewResult;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.ExamDraftResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.AttemptAnswer;
import com.englow3.exam.entity.AttemptAnswerOption;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptDraft;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.AttemptSectionResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.ScoreConversion;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.helper.AnswerKey;
import com.englow3.exam.helper.BandConversion;
import com.englow3.exam.helper.QuestionNumbering;
import com.englow3.exam.repository.AttemptSectionResultRepository;
import com.englow3.exam.repository.ScoreConversionRepository;
import com.englow3.exam.query.ExamGradingQuery.GradingQuestion;
import com.englow3.exam.query.ExamGradingQuery;
import com.englow3.exam.query.ExamOutlineQuery;
import com.englow3.exam.query.LearnerExamPaperQuery;
import com.englow3.exam.repository.AttemptAnswerOptionRepository;
import com.englow3.exam.repository.AttemptAnswerRepository;
import com.englow3.exam.repository.ExamAttemptDraftRepository;
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
    private final ExamAttemptDraftRepository draftRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final ExamOutlineQuery outlineQuery;
    private final Duration fullAttemptGrace;
    private final ApplicationEventPublisher events;
    private final ScoreConversionRepository conversionRepo;
    private final AttemptSectionResultRepository sectionResultRepo;

    /** The longest clock a learner can set on a practice. */
    static final int MAX_PRACTICE_MINUTES = 300;

    public ExamAttemptServiceImpl(ExamRepository examRepo, ExamAttemptRepository attemptRepo,
            AttemptAnswerRepository answerRepo, AttemptAnswerOptionRepository answerOptionRepo,
            LearnerExamPaperQuery paperQuery, ExamGradingQuery gradingQuery, UserDirectory userDirectory,
            PlacementRecorder placementRecorder, PresignedUrlResolver presignedUrls, Clock clock,
            @Value("${app.storage.exam-bucket}") String examBucket,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl, ExamAttemptDraftRepository draftRepo,
            com.fasterxml.jackson.databind.ObjectMapper mapper, ExamOutlineQuery outlineQuery,
            @Value("${app.exam.full-attempt-grace:PT2M}") Duration fullAttemptGrace, ApplicationEventPublisher events,
            ScoreConversionRepository conversionRepo, AttemptSectionResultRepository sectionResultRepo) {
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
        this.draftRepo = draftRepo;
        this.mapper = mapper;
        this.outlineQuery = outlineQuery;
        this.fullAttemptGrace = fullAttemptGrace;
        this.events = events;
        this.conversionRepo = conversionRepo;
        this.sectionResultRepo = sectionResultRepo;
    }

    @Transactional(readOnly = true)
    public Page<ExamAttemptResult> attemptHistory(Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<ExamAttempt> page = attemptRepo.findByUserIdOrderByStartedAtDesc(userId, pageable);
        Map<UUID, String> titles = page.getContent().isEmpty() ? Map.of()
                : examRepo.findAllById(page.getContent().stream().map(ExamAttempt::getExamId).toList()).stream()
                        .collect(Collectors.toMap(Exam::getId, Exam::getTitle));
        Map<UUID, ExamOutlineQuery.OutlinePart> parts = outlineQuery.describe(page.getContent().stream()
                .flatMap(attempt -> attempt.getPartIds().stream()).collect(Collectors.toSet()));
        return page
                .map(attempt -> ExamAttemptResult.summary(attempt, titles.get(attempt.getExamId())).withParts(parts));
    }

    @Transactional
    public ExamAttemptResult start(UUID examId) {
        return start(StartExamAttemptCommand.full(examId));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ExamAttemptResult> activeAttempt(UUID examId, ExamAttemptMode mode) {
        requirePublishedExam(examId);
        return attemptRepo
                .findFirstByUserIdAndExamIdAndModeAndStatusOrderByStartedAtDesc(userDirectory.requireCurrentUserId(),
                        examId, mode, ExamAttemptStatus.IN_PROGRESS)
                .filter(attempt -> clock.instant().isBefore(attempt.getExpiresAt()))
                .filter(attempt -> !attempt.isAbandoned(clock.instant(), fullAttemptGrace))
                .map(attempt -> named(ExamAttemptResult.started(attempt, true)));
    }

    /**
     * Opens a paper as a full attempt or as a practice of some of its parts.
     * <p>
     * Each mode has its own active attempt. Replacement is explicit even when the configuration is unchanged.
     */
    @Transactional
    public ExamAttemptResult start(StartExamAttemptCommand command) {
        Exam exam = requirePublishedExam(command.examId());
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = clock.instant();
        PracticePlan practice = command.mode() == ExamAttemptMode.PRACTICE ? planPractice(exam, command) : null;

        var active = attemptRepo.findFirstByUserIdAndExamIdAndModeAndStatusOrderByStartedAtDesc(userId, exam.getId(),
                command.mode(), ExamAttemptStatus.IN_PROGRESS);
        if (active.isPresent() && active.get().isAbandoned(now, fullAttemptGrace)) {
            // The real test is not picked up again after it was left: it ends, and this start is a fresh sitting.
            finalizeAbandonedLocked(active.get().getId(), now);
            attemptRepo.flush();
            active = java.util.Optional.empty();
        }
        if (active.isPresent() && now.isBefore(active.get().getExpiresAt())) {
            ExamAttempt open = active.get();
            boolean asked = practice == null ? open.matches(ExamAttemptMode.FULL, Set.of(), exam.getDurationSeconds())
                    : open.matches(ExamAttemptMode.PRACTICE, practice.partIds(), practice.timeLimitSeconds());
            if ((asked && command.onOpen() != StartExamAttemptCommand.OpenAttempt.REPLACE)
                    || command.onOpen() == StartExamAttemptCommand.OpenAttempt.RESUME) {
                return named(ExamAttemptResult.started(open, true));
            }
            if (command.onOpen() != StartExamAttemptCommand.OpenAttempt.REPLACE) {
                throw new ConflictException("ATTEMPT_IN_PROGRESS", open.isPractice()
                        ? "A practice of this exam is still open; resume it or finish it before starting another"
                        : "A full attempt at this exam is still open; resume it or finish it before starting another");
            }
            // Finalized now, from what was saved - the same as submitting it, which is what the learner chose.
            ExamAttempt locked = attemptRepo.findByIdForUpdate(open.getId()).orElseThrow();
            if (locked.getStatus() == ExamAttemptStatus.IN_PROGRESS) {
                scoreAttempt(locked, storedAnswers(locked.getId()), now);
            }
            attemptRepo.flush();
        } else if (active.isPresent()) {
            ExamAttempt locked = attemptRepo.findByIdForUpdate(active.get().getId()).orElseThrow();
            if (locked.getStatus() == ExamAttemptStatus.IN_PROGRESS) {
                scoreAttempt(locked, storedAnswers(locked.getId()), locked.getExpiresAt());
            }
            attemptRepo.flush();
        }

        if (command.onOpen() == StartExamAttemptCommand.OpenAttempt.RESUME) {
            throw new ConflictException("ATTEMPT_NO_LONGER_ACTIVE", "This attempt is no longer available to resume");
        }
        ExamAttempt attempt;
        if (practice == null) {
            int questionCount = Math.toIntExact(examRepo.countQuestions(exam.getId()));
            // Out of what the questions carry, not the figure the paper declares: the two disagree on some older
            // papers.
            BigDecimal questionsWorth = examRepo.sumQuestionScores(exam.getId());
            BigDecimal maxRawScore = questionsWorth != null && questionsWorth.signum() > 0 ? questionsWorth
                    : exam.getMaxRawScore();
            attempt = ExamAttempt.start(exam, userId, questionCount, maxRawScore, now);
        } else {
            attempt = ExamAttempt.startPractice(exam, userId, practice.partIds(), practice.questionCount(),
                    practice.maxRawScore(), practice.timeLimitSeconds(), now);
        }
        return named(ExamAttemptResult.started(attemptRepo.save(attempt), false));
    }

    /** The parts, totals and clock of a practice, checked against the paper before anything is written. */
    private record PracticePlan(Set<UUID> partIds, int questionCount, BigDecimal maxRawScore,
            Integer timeLimitSeconds) {
    }

    private PracticePlan planPractice(Exam exam, StartExamAttemptCommand command) {
        if (exam.getExamType() == ExamType.PLACEMENT || exam.getExamType() == ExamType.LEVEL_CHECK) {
            throw new BadRequestException("PRACTICE_NOT_AVAILABLE",
                    "A placement or level test can only be taken in full - it decides the learner's level");
        }
        if (command.partIds() == null || command.partIds().isEmpty()) {
            throw new BadRequestException("PRACTICE_PARTS_REQUIRED", "Choose at least one part to practise");
        }
        Integer minutes = command.timeLimitMinutes();
        if (minutes != null && (minutes < 1 || minutes > MAX_PRACTICE_MINUTES)) {
            throw new BadRequestException("PRACTICE_TIME_LIMIT_INVALID",
                    "A practice time limit is between 1 and %d minutes".formatted(MAX_PRACTICE_MINUTES));
        }
        Map<UUID, ExamOutlineQuery.OutlinePart> parts = outlineQuery.load(exam.getId()).stream()
                .collect(Collectors.toMap(ExamOutlineQuery.OutlinePart::id, Function.identity()));
        long questions = 0;
        BigDecimal maxRawScore = BigDecimal.ZERO;
        for (UUID partId : command.partIds()) {
            ExamOutlineQuery.OutlinePart part = parts.get(partId);
            if (part == null) {
                throw new BadRequestException("PART_NOT_IN_EXAM",
                        "Part %s does not belong to this exam".formatted(partId));
            }
            questions += part.questionCount();
            maxRawScore = maxRawScore.add(part.maxRawScore());
        }
        if (questions == 0) {
            throw new BadRequestException("PRACTICE_HAS_NO_QUESTIONS", "The chosen parts hold no questions");
        }
        return new PracticePlan(Set.copyOf(command.partIds()), Math.toIntExact(questions), maxRawScore,
                minutes == null ? null : minutes * 60);
    }

    /** The attempt with its practised parts named, for the screens that list them. */
    private ExamAttemptResult named(ExamAttemptResult result) {
        if (result.parts().isEmpty()) {
            return result;
        }
        return result.withParts(
                outlineQuery.describe(result.parts().stream().map(ExamAttemptResult.AttemptPart::id).toList()));
    }

    /** What this attempt is graded on: the whole paper, or only the parts a practice covers. */
    private List<GradingQuestion> gradingFor(ExamAttempt attempt) {
        return attempt.isPractice() ? gradingQuery.load(attempt.getExamId(), attempt.getPartIds())
                : gradingQuery.load(attempt.getExamId());
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
        requireNotAbandoned(attempt, clock.instant());
        var paper = attempt.isPractice() ? paperQuery.load(attempt.getExamId(), attempt.getPartIds())
                : paperQuery.load(attempt.getExamId());
        LearnerExamPaperProjection loaded = paper.orElseThrow(() -> examNotFound(attempt.getExamId()));
        List<GradingQuestion> wholePaper = gradingQuery.load(attempt.getExamId());
        return toResult(loaded, numbersFor(loaded.exam(), wholePaper),
                wholePaper.stream().collect(Collectors.toMap(GradingQuestion::id, GradingQuestion::key)));
    }

    @Transactional
    public ExamAttemptResult submit(SubmitExamAttemptCommand command) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findByIdForUpdate(command.attemptId()),
                command.attemptId());
        Instant now = clock.instant();
        if (attempt.getStatus() == ExamAttemptStatus.SCORED) {
            return scoredResult(attempt);
        }
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("ATTEMPT_ALREADY_FINALIZED", "This exam attempt has already been finalized");
        }
        if (!now.isBefore(attempt.getExpiresAt())) {
            return scoreAttempt(attempt, storedAnswers(attempt.getId()), attempt.getExpiresAt());
        }
        if (attempt.isAbandoned(now, fullAttemptGrace)) {
            // The sitting ended when the learner left it, as heartbeat and start already treat it; the deadline worker
            // has simply not reached it yet. Answers sent from a screen that came back afterwards do not count.
            return scoreAttempt(attempt, storedAnswers(attempt.getId()), attempt.lastSeen());
        }
        return scoreAttempt(attempt, command.answers(), now);
    }

    private ExamAttemptResult scoreAttempt(ExamAttempt attempt, List<SubmittedAnswer> submittedAnswers, Instant now) {
        List<GradingQuestion> questions = gradingFor(attempt);
        Map<UUID, GradingQuestion> questionById = questions.stream().collect(
                Collectors.toMap(GradingQuestion::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        validateSubmission(submittedAnswers, questionById);

        BigDecimal rawScore = BigDecimal.ZERO;
        int correctCount = 0;
        List<AttemptAnswer> storedAnswers = new ArrayList<>();
        List<AttemptAnswerOption> storedOptions = new ArrayList<>();

        for (SubmittedAnswer submitted : submittedAnswers) {
            GradingQuestion question = questionById.get(submitted.questionId());
            String text = question.questionType() == QuestionType.TEXT_ENTRY ? blankToNull(submitted.textAnswer())
                    : null;
            AnswerKey.Grade grade = question.key().grade(new HashSet<>(submitted.selectedOptionIds()), text);
            rawScore = rawScore.add(grade.awarded());
            if (grade.correct()) {
                correctCount++;
            }

            AttemptAnswer answer = AttemptAnswer.graded(attempt.getId(), question.id(), text, grade.correct(),
                    grade.awarded(), now);
            storedAnswers.add(answer);
            boolean fromSet = question.questionType() == QuestionType.MATCHING;
            submitted.selectedOptionIds()
                    .forEach(optionId -> storedOptions
                            .add(fromSet ? AttemptAnswerOption.selectedFromSet(answer.getId(), optionId)
                                    : AttemptAnswerOption.selected(answer.getId(), optionId)));
        }

        answerRepo.saveAll(storedAnswers);
        answerOptionRepo.saveAll(storedOptions);
        attempt.score(rawScore, correctCount, now);
        Exam exam = examRepo.findById(attempt.getExamId()).orElseThrow(() -> examNotFound(attempt.getExamId()));
        List<AttemptSectionResult> sections = sectionResults(exam, attempt, questions, storedAnswers, now);
        sectionResultRepo.saveAll(sections);
        if (exam.getCertificateType() == CertificateType.TOEIC && !attempt.isPractice()) {
            com.englow3.exam.helper.ToeicScoring.total(sections).ifPresent(attempt::recordConvertedScore);
        }
        events.publishEvent(new ExamSittingScored(attempt.getId(), attempt.getUserId(), attempt.getExamId(),
                !attempt.isPractice()));

        // A practice never sets a level: it is a chosen slice of the paper, on a clock the learner picked.
        if (exam.getExamType() == ExamType.PLACEMENT && !attempt.isPractice()) {
            placementRecorder.record(attempt.getUserId(), attempt.getId(), attempt.getScorePercentage(),
                    attempt.getStartedAt());
        }

        return named(ExamAttemptResult.scored(attempt, buildReview(exam, questions, storedAnswers, storedOptions),
                sectionScores(sections)));
    }

    /**
     * Each section's points, and its band where the certificate converts a whole section. A practice never gets a band:
     * it covers a chosen slice on a clock the learner picked, and the tables are written for the full section.
     */
    private List<AttemptSectionResult> sectionResults(Exam exam, ExamAttempt attempt, List<GradingQuestion> questions,
            List<AttemptAnswer> answers, Instant now) {
        Map<UUID, AttemptAnswer> answerByQuestion = answers.stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));
        Map<SectionType, List<GradingQuestion>> bySection = questions.stream()
                .collect(Collectors.groupingBy(GradingQuestion::sectionType, LinkedHashMap::new, Collectors.toList()));
        List<ScoreConversion> conversions = exam.getCertificateType() == null || attempt.isPractice() ? List.of()
                : conversionRepo.findByCertificateType(exam.getCertificateType());
        LocalDate scoredOn = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<AttemptSectionResult> results = new ArrayList<>();
        bySection.forEach((sectionType, sectionQuestions) -> {
            BigDecimal max = BigDecimal.ZERO;
            BigDecimal raw = BigDecimal.ZERO;
            int correct = 0;
            for (GradingQuestion question : sectionQuestions) {
                max = max.add(question.maxRawScore());
                AttemptAnswer answer = answerByQuestion.get(question.id());
                if (answer != null) {
                    raw = raw.add(answer.getAwardedRawScore());
                    correct += answer.isCorrect() ? 1 : 0;
                }
            }
            boolean toeic = exam.getCertificateType() == CertificateType.TOEIC;
            boolean toeicShape = !toeic
                    || sectionQuestions.stream().allMatch(q -> q.questionType() == QuestionType.SINGLE_CHOICE);
            BigDecimal band = toeicShape ? BandConversion.convert(conversions, exam.getCertificateVariant(),
                    sectionType, toeic ? BigDecimal.valueOf(correct) : raw,
                    toeic ? BigDecimal.valueOf(sectionQuestions.size()) : max, scoredOn).orElse(null) : null;
            results.add(AttemptSectionResult.of(attempt.getId(), sectionType, raw, max, band, correct,
                    sectionQuestions.size()));
        });
        return results;
    }

    private static List<ExamAttemptResult.SectionScore> sectionScores(List<AttemptSectionResult> sections) {
        return sections.stream()
                .map(section -> new ExamAttemptResult.SectionScore(section.getSectionType(), section.getRawScore(),
                        section.getMaxRawScore(), section.getConvertedScore(), section.getCorrectAnswerCount(),
                        section.getQuestionCount()))
                .toList();
    }

    /** Question numbers over the whole paper, so a practice keeps the numbers its parts have in the full test. */
    private static Map<UUID, QuestionNumbering.Numbers> numbersFor(Exam exam, List<GradingQuestion> wholePaper) {
        return QuestionNumbering.assign(
                wholePaper.stream()
                        .map(question -> new QuestionNumbering.Slot(question.id(), question.sectionId(),
                                question.key().numberSpan()))
                        .toList(),
                exam.getCertificateType() == CertificateType.IELTS);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    @Transactional
    public ExamAttemptResult result(UUID attemptId) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findByIdForUpdate(attemptId), attemptId);
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS && !clock.instant().isBefore(attempt.getExpiresAt())) {
            return scoreAttempt(attempt, storedAnswers(attemptId), attempt.getExpiresAt());
        }
        return scoredResult(attempt);
    }

    private ExamAttemptResult scoredResult(ExamAttempt attempt) {
        UUID attemptId = attempt.getId();
        if (attempt.getStatus() != ExamAttemptStatus.SCORED) {
            throw new ConflictException("ATTEMPT_RESULT_NOT_READY", "This exam attempt has not been scored yet");
        }
        List<AttemptAnswer> answers = answerRepo.findByExamAttemptId(attemptId);
        List<UUID> answerIds = answers.stream().map(AttemptAnswer::getId).toList();
        List<AttemptAnswerOption> selectedOptions = answerIds.isEmpty() ? List.of()
                : answerOptionRepo.findByAttemptAnswerIdIn(answerIds);
        Exam exam = examRepo.findById(attempt.getExamId()).orElseThrow(() -> examNotFound(attempt.getExamId()));
        List<ExamAttemptResult.SectionScore> sections = sectionScores(sectionResultRepo.findByExamAttemptId(attemptId))
                .stream().sorted(Comparator.comparing(ExamAttemptResult.SectionScore::sectionType)).toList();
        return named(ExamAttemptResult.scored(attempt, buildReview(exam, gradingFor(attempt), answers, selectedOptions),
                sections));
    }

    @Transactional(readOnly = true)
    public ExamDraftResult draft(UUID id) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findById(id), id);
        var saved = draftRepo.findById(id);
        return new ExamDraftResult(storedAnswers(id), saved.map(d -> d.getRevision()).orElse(0L),
                saved.map(d -> d.getSavedAt()).orElse(attempt.getStartedAt()));
    }

    @Transactional
    public ExamDraftResult saveDraft(SaveExamDraftCommand c) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findByIdForUpdate(c.attemptId()), c.attemptId());
        Instant now = clock.instant();
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS || !now.isBefore(attempt.getExpiresAt())) {
            throw new ConflictException("ATTEMPT_EXPIRED", "Answers can only be saved before the deadline");
        }
        // Before touch(): a save would otherwise revive a sitting that already ended, which heartbeat refuses to do.
        requireNotAbandoned(attempt, now);
        Map<UUID, GradingQuestion> questions = gradingFor(attempt).stream()
                .collect(Collectors.toMap(GradingQuestion::id, Function.identity()));
        validateSubmission(c.answers(), questions);
        var draft = draftRepo.findById(c.attemptId()).orElseGet(() -> ExamAttemptDraft.empty(c.attemptId(), now));
        try {
            draft.replace(c.version(), mapper.writeValueAsString(c.answers()), now);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize exam draft");
        }
        draftRepo.saveAndFlush(draft);
        attempt.touch(now);
        return new ExamDraftResult(c.answers(), draft.getRevision(), draft.getSavedAt());
    }

    /**
     * The browser saying it is still on this sitting. Answers whether the sitting is still open, so a screen that was
     * away past the grace period learns it was ended rather than finding out on its next save.
     */
    @Transactional
    public boolean heartbeat(UUID attemptId) {
        ExamAttempt attempt = requireOwnedAttempt(attemptRepo.findByIdForUpdate(attemptId), attemptId);
        Instant now = clock.instant();
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS || !now.isBefore(attempt.getExpiresAt())) {
            return false;
        }
        if (attempt.isAbandoned(now, fullAttemptGrace)) {
            finalizeAbandonedLocked(attemptId, now);
            return false;
        }
        attempt.touch(now);
        return true;
    }

    /** Ends a full sitting nobody has been at, scoring what was saved. The deadline worker's second job. */
    @Transactional
    public void finalizeAbandoned(UUID id) {
        finalizeAbandonedLocked(id, clock.instant());
    }

    private void finalizeAbandonedLocked(UUID id, Instant now) {
        var candidate = attemptRepo.findByIdForUpdate(id);
        if (candidate.isEmpty() || !candidate.get().isAbandoned(now, fullAttemptGrace)) {
            return;
        }
        ExamAttempt attempt = candidate.get();
        // Dated to the last moment the learner was there, not to when the worker noticed.
        scoreAttempt(attempt, storedAnswers(id), attempt.lastSeen());
    }

    @Transactional
    public void finalizeExpired(UUID id) {
        var candidate = attemptRepo.findByIdForUpdate(id);
        if (candidate.isEmpty()) {
            return;
        }
        var attempt = candidate.get();
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS && !clock.instant().isBefore(attempt.getExpiresAt())) {
            scoreAttempt(attempt, storedAnswers(id), attempt.getExpiresAt());
        }
    }

    private List<SubmittedAnswer> storedAnswers(UUID id) {
        String json = draftRepo.findById(id).map(d -> d.getAnswers()).orElse("[]");
        try {
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<SubmittedAnswer>>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Stored exam draft is invalid");
        }
    }

    /** The numbers and choice limits travel alongside the tree: the paper itself carries no answer key. */
    private record PaperNumbers(Map<UUID, QuestionNumbering.Numbers> numbers, Map<UUID, AnswerKey> keys) {
    }

    private LearnerExamPaperResult toResult(LearnerExamPaperProjection paper,
            Map<UUID, QuestionNumbering.Numbers> numbers, Map<UUID, AnswerKey> keys) {
        PaperNumbers extra = new PaperNumbers(numbers, keys);
        return LearnerExamPaperResult.of(paper.exam(),
                paper.sections().stream().map(section -> toSection(section, extra)).toList());
    }

    private LearnerExamPaperResult.LearnerSection toSection(Section section, PaperNumbers extra) {
        return new LearnerExamPaperResult.LearnerSection(section.id(), section.sectionType(), section.orderNo(),
                section.maxRawScore(), section.scoredByCriteria(), section.timeLimitSeconds(),
                section.parts().stream().map(part -> toPart(part, extra)).toList());
    }

    private LearnerExamPaperResult.LearnerPart toPart(Part part, PaperNumbers extra) {
        return new LearnerExamPaperResult.LearnerPart(part.id(), part.orderNo(), part.title(), part.instruction(),
                part.content(), presignedUrls.resolve(examBucket, part.audioObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(examBucket, part.imageObjectKey(), mediaUrlTtl),
                part.questionSets().stream().map(set -> toQuestionSet(set, extra)).toList());
    }

    private LearnerExamPaperResult.QuestionSetResult toQuestionSet(QuestionSet questionSet, PaperNumbers extra) {
        return new LearnerExamPaperResult.QuestionSetResult(questionSet.id(), questionSet.title(),
                questionSet.instruction(), questionSet.orderNo(), questionSet.content(),
                presignedUrls.resolve(examBucket, questionSet.audioObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(examBucket, questionSet.imageObjectKey(), mediaUrlTtl),
                questionSet.questions().stream().map(question -> toQuestion(question, extra)).toList(),
                questionSet.setOptions().stream().map(entry -> new LearnerExamPaperResult.SetOptionResult(entry.id(),
                        entry.label(), entry.content(), entry.orderNo())).toList());
    }

    private LearnerExamPaperResult.QuestionResult toQuestion(Question question, PaperNumbers extra) {
        QuestionNumbering.Numbers number = extra.numbers().getOrDefault(question.id(),
                new QuestionNumbering.Numbers(question.orderNo(), question.orderNo()));
        AnswerKey key = extra.keys().get(question.id());
        int choiceLimit = question.questionType() == QuestionType.TEXT_ENTRY ? 0 : key == null ? 1 : key.choiceLimit();
        return new LearnerExamPaperResult.QuestionResult(question.id(), question.questionType(), question.content(),
                question.difficultyLevel(), question.skillType(), question.questionCategory(), question.orderNo(),
                question.maxRawScore(),
                question.options().stream().map(option -> new LearnerExamPaperResult.QuestionOptionResult(option.id(),
                        option.content(), option.orderNo())).toList(),
                number.from(), number.to(), choiceLimit);
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
            if (question.questionType() == QuestionType.TEXT_ENTRY) {
                continue;
            }
            if (answer.textAnswer() != null && !answer.textAnswer().isBlank()) {
                throw new BadRequestException("TEXT_ANSWER_NOT_ACCEPTED",
                        "Question %s is answered by choosing options, not by typing".formatted(answer.questionId()));
            }
            int limit = question.key().choiceLimit();
            if (selected.size() > Math.max(1, limit)) {
                throw new BadRequestException("TOO_MANY_SELECTED_OPTIONS",
                        "Question %s accepts at most %d option(s)".formatted(answer.questionId(), Math.max(1, limit)));
            }
        }
    }

    private List<QuestionReviewResult> buildReview(Exam exam, List<GradingQuestion> questions,
            List<AttemptAnswer> answers, List<AttemptAnswerOption> selectedOptions) {
        Map<UUID, QuestionNumbering.Numbers> numbers = numbersFor(exam,
                questions.isEmpty() ? List.of() : gradingQuery.load(exam.getId()));
        Map<UUID, AttemptAnswer> answerByQuestion = answers.stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, Function.identity()));
        Map<UUID, List<UUID>> optionsByAnswer = new HashMap<>();
        if (!answers.isEmpty()) {
            Map<UUID, UUID> answerQuestionIds = answers.stream()
                    .collect(Collectors.toMap(AttemptAnswer::getId, AttemptAnswer::getQuestionId));
            for (AttemptAnswerOption option : selectedOptions) {
                UUID answerId = option.getAttemptAnswerId();
                optionsByAnswer.computeIfAbsent(answerQuestionIds.get(answerId), ignored -> new ArrayList<>())
                        .add(option.chosenId());
            }
        }

        return questions.stream().map(question -> {
            AttemptAnswer answer = answerByQuestion.get(question.id());
            List<UUID> correctIds = question.options().stream().filter(option -> option.correct())
                    .map(option -> option.id()).toList();
            List<OptionReviewResult> options = question.options().stream()
                    .map(option -> new OptionReviewResult(option.id(), option.correct(), option.explanation()))
                    .toList();
            QuestionNumbering.Numbers number = numbers.getOrDefault(question.id(), new QuestionNumbering.Numbers(0, 0));
            return new QuestionReviewResult(question.id(), optionsByAnswer.getOrDefault(question.id(), List.of()),
                    correctIds, answer != null && answer.isCorrect(),
                    answer == null ? BigDecimal.ZERO : answer.getAwardedRawScore(), question.explanation(), options,
                    answer == null ? null : answer.getTextAnswer(),
                    question.acceptedAnswers().stream().map(ExamGradingQuery.GradingAcceptedAnswer::text).toList(),
                    number.from(), number.to());
        }).toList();
    }

    /**
     * A full sitting left for longer than the grace is over even before the deadline worker finalizes it. Refused with
     * the expiry code on purpose: to the screen both mean the sitting has ended and its result is what comes next.
     */
    private void requireNotAbandoned(ExamAttempt attempt, Instant now) {
        if (attempt.isAbandoned(now, fullAttemptGrace)) {
            throw new ConflictException("ATTEMPT_EXPIRED", "This sitting ended when it was left");
        }
    }

    private Exam requirePublishedExam(UUID examId) {
        return examRepo.findById(examId).filter(exam -> exam.getStatus() == ExamStatus.PUBLISHED)
                .orElseThrow(() -> examNotFound(examId));
    }

    private ExamAttempt requireOwnedAttempt(Optional<ExamAttempt> candidate, UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        return candidate.filter(attempt -> attempt.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("EXAM_ATTEMPT_NOT_FOUND",
                        "No exam attempt with id %s".formatted(attemptId)));
    }

    private static NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No published exam with id %s".formatted(examId));
    }
}
