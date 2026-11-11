package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.query.ExamOutlineQuery.OutlinePart;

public record ExamAttemptResult(UUID id, UUID examId, ExamAttemptStatus status, Instant startedAt, Instant expiresAt,
        Instant submittedAt, Instant scoredAt, BigDecimal rawScore, BigDecimal maxRawScore, BigDecimal scorePercentage,
        Integer correctAnswerCount, int questionCount, boolean resumed, String examTitle,
        List<QuestionReviewResult> questions, ExamAttemptMode mode, Integer timeLimitSeconds, List<AttemptPart> parts,
        List<SectionScore> sections, BigDecimal convertedScore) {

    /**
     * One section's points and, where the certificate converts a whole section, its band. Empty until scored.
     *
     * @param band
     *            null where no conversion applies: a practice, a TOEIC section, a section not out of the table's total
     */
    public record SectionScore(SectionType sectionType, BigDecimal rawScore, BigDecimal maxRawScore, BigDecimal band,
            Integer correctAnswerCount, int questionCount) {
    }

    /** A part a practice covered, named so a history row can say what was practised. */
    public record AttemptPart(UUID id, SectionType sectionType, int sectionOrderNo, int orderNo, String title) {
    }

    /** The same attempt with its parts named. Parts the lookup does not know are dropped rather than shown blank. */
    public ExamAttemptResult withParts(Map<UUID, OutlinePart> known) {
        List<AttemptPart> named = parts.stream().map(part -> known.get(part.id())).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(OutlinePart::sectionOrderNo).thenComparingInt(OutlinePart::orderNo))
                .map(part -> new AttemptPart(part.id(), part.sectionType(), part.sectionOrderNo(), part.orderNo(),
                        part.title()))
                .toList();
        return new ExamAttemptResult(id, examId, status, startedAt, expiresAt, submittedAt, scoredAt, rawScore,
                maxRawScore, scorePercentage, correctAnswerCount, questionCount, resumed, examTitle, questions, mode,
                timeLimitSeconds, named, sections, convertedScore);
    }

    private static List<AttemptPart> unnamed(ExamAttempt attempt) {
        return attempt.getPartIds().stream().map(id -> new AttemptPart(id, null, 0, 0, null)).toList();
    }

    /**
     * An attempt as a history row: everything except the review, which carries the answer key and is not something a
     * list of past sittings should hand out.
     */
    public static ExamAttemptResult summary(ExamAttempt attempt, String examTitle) {
        return new ExamAttemptResult(attempt.getId(), attempt.getExamId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getExpiresAt(), attempt.getSubmittedAt(), attempt.getScoredAt(), attempt.getRawScore(),
                attempt.getMaxRawScore(), attempt.getScorePercentage(), attempt.getCorrectAnswerCount(),
                attempt.getQuestionCount(), false, examTitle, List.of(), attempt.getMode(),
                attempt.getTimeLimitSeconds(), unnamed(attempt), List.of(), attempt.getConvertedScore());
    }

    public static ExamAttemptResult started(ExamAttempt attempt, boolean resumed) {
        return from(attempt, resumed, List.of(), List.of());
    }

    public static ExamAttemptResult scored(ExamAttempt attempt, List<QuestionReviewResult> questions,
            List<SectionScore> sections) {
        return from(attempt, false, questions, sections);
    }

    private static ExamAttemptResult from(ExamAttempt attempt, boolean resumed, List<QuestionReviewResult> questions,
            List<SectionScore> sections) {
        return new ExamAttemptResult(attempt.getId(), attempt.getExamId(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getExpiresAt(), attempt.getSubmittedAt(), attempt.getScoredAt(), attempt.getRawScore(),
                attempt.getMaxRawScore(), attempt.getScorePercentage(), attempt.getCorrectAnswerCount(),
                attempt.getQuestionCount(), resumed, null, questions, attempt.getMode(), attempt.getTimeLimitSeconds(),
                unnamed(attempt), sections, attempt.getConvertedScore());
    }

    /**
     * @param textAnswer
     *            what was typed on a TEXT_ENTRY question; null on a choice question or when it was left blank
     * @param acceptedAnswers
     *            the spellings a TEXT_ENTRY question accepts; empty on a choice question
     * @param numberFrom
     *            the question's number on the paper; {@code numberTo} differs only on a "choose N" question
     */
    public record QuestionReviewResult(UUID questionId, List<UUID> selectedOptionIds, List<UUID> correctOptionIds,
            boolean correct, BigDecimal awardedRawScore, String explanation, List<OptionReviewResult> options,
            String textAnswer, List<String> acceptedAnswers, int numberFrom, int numberTo) {
    }

    public record OptionReviewResult(UUID optionId, boolean correct, String explanation) {
    }
}
