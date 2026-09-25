package com.englow3.learning.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.command.SubmitDictationCommand;
import com.englow3.learning.dto.result.DictationLessonDetailResult;
import com.englow3.learning.dto.result.DictationLessonSummaryResult;
import com.englow3.learning.dto.result.DictationSentenceResult;
import com.englow3.learning.dto.result.DictationSubmissionResult;
import com.englow3.learning.entity.DictationAttempt;
import com.englow3.learning.entity.DictationLesson;
import com.englow3.learning.entity.DictationLessonStatus;
import com.englow3.learning.entity.DictationSentence;
import com.englow3.learning.repository.DictationAttemptRepository;
import com.englow3.learning.repository.DictationLessonRepository;
import com.englow3.learning.repository.DictationSentenceRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Practising dictation. The rule this class exists to enforce is that the transcript never leaves the server before the
 * learner has typed theirs - a screen that could read the answer is a screen that could show it.
 */
@Service
@RequiredArgsConstructor
public class DictationService {

    private final DictationLessonRepository lessonRepo;
    private final DictationSentenceRepository sentenceRepo;
    private final DictationAttemptRepository attemptRepo;
    private final UserDirectory userDirectory;

    @Transactional(readOnly = true)
    public Page<DictationLessonSummaryResult> searchPublished(String topic, String title, Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<DictationLesson> page = lessonRepo.searchByStatus(DictationLessonStatus.PUBLISHED, topic, title, pageable);

        List<UUID> lessonIds = page.getContent().stream().map(DictationLesson::getId).toList();
        // Three queries for the whole page, not two per lesson: every sentence at once, then the learner's best on
        // every sentence at once. Per lesson it was a round trip to the database each, which on a page of twenty is
        // seconds.
        List<DictationSentence> allSentences = lessonIds.isEmpty() ? List.of()
                : sentenceRepo.findByDictationLessonIdInOrderByOrderNo(lessonIds);
        Map<UUID, List<DictationSentence>> sentencesByLesson = allSentences.stream()
                .collect(Collectors.groupingBy(DictationSentence::getDictationLessonId));
        Map<UUID, BigDecimal> best = bestAccuracyFor(userId, allSentences);
        Map<UUID, Instant> lastPractised = lastPractisedFor(userId, lessonIds);

        return page.map(lesson -> {
            List<DictationSentence> sentences = sentencesByLesson.getOrDefault(lesson.getId(), List.of());
            long completed = sentences.stream().map(sentence -> best.get(sentence.getId()))
                    .filter(DictationScorer::cleared).count();
            return DictationLessonSummaryResult.of(lesson, sentences.size(), completed, totalDuration(sentences),
                    lastPractised.get(lesson.getId()));
        });
    }

    /**
     * The practice payload. Audio and hints only - {@link DictationSentenceResult} has no field for the transcript, so
     * there is nothing here to forget to strip.
     */
    @Transactional(readOnly = true)
    public DictationLessonDetailResult lessonDetail(UUID lessonId) {
        UUID userId = userDirectory.requireCurrentUserId();
        DictationLesson lesson = requirePublished(lessonId);
        List<DictationSentence> sentences = sentenceRepo.findByDictationLessonIdOrderByOrderNo(lessonId);
        Map<UUID, BigDecimal> best = bestAccuracyFor(userId, sentences);

        return new DictationLessonDetailResult(
                DictationLessonSummaryResult.of(lesson, sentences.size(), completedCount(userId, sentences),
                        totalDuration(sentences), lastPractisedFor(userId, List.of(lessonId)).get(lessonId)),
                sentences.stream()
                        .map(sentence -> new DictationSentenceResult(sentence.getId(), sentence.getOrderNo(),
                                sentence.getAudioObjectKey(), sentence.getAudioDurationSeconds(),
                                sentence.getHintWordCount(), sentence.getHintFirstLetters(),
                                sentence.getHintRevealWord(), sentence.getHintPartialTranscript(),
                                sentence.getAudioStartMs(), sentence.getAudioEndMs(), best.get(sentence.getId())))
                        .toList());
    }

    /**
     * Marks one line and records the attempt. The transcript comes back in the response - this is the first point at
     * which the learner is entitled to it, because they have already committed an answer.
     */
    @Transactional
    public DictationSubmissionResult submit(SubmitDictationCommand command) {
        UUID userId = userDirectory.requireCurrentUserId();
        DictationSentence sentence = sentenceRepo.findById(command.sentenceId())
                .orElseThrow(() -> new NotFoundException("DICTATION_SENTENCE_NOT_FOUND",
                        "No dictation sentence with id %s".formatted(command.sentenceId())));
        requirePublished(sentence.getDictationLessonId());

        String response = command.response() == null ? "" : command.response();
        DictationScorer.Score score = DictationScorer.score(sentence.getText(), response);

        attemptRepo.save(DictationAttempt.of(userId, sentence.getDictationLessonId(), sentence.getId(), response,
                score.accuracyPercent(), score.correctWordCount(), score.totalWordCount(), Instant.now()));

        return new DictationSubmissionResult(sentence.getId(), sentence.getText(), sentence.getTranslationVi(),
                response, score.accuracyPercent(), score.correctWordCount(), score.totalWordCount(),
                DictationScorer.cleared(score.accuracyPercent()));
    }

    private long completedCount(UUID userId, List<DictationSentence> sentences) {
        return bestAccuracyFor(userId, sentences).values().stream().filter(DictationScorer::cleared).count();
    }

    private static int totalDuration(List<DictationSentence> sentences) {
        return sentences.stream().mapToInt(DictationSentence::getAudioDurationSeconds).sum();
    }

    private Map<UUID, BigDecimal> bestAccuracyFor(UUID userId, List<DictationSentence> sentences) {
        if (sentences.isEmpty()) {
            return Map.of();
        }
        return attemptRepo.findBestAccuracyBySentence(userId, sentences.stream().map(DictationSentence::getId).toList())
                .stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
    }

    private Map<UUID, Instant> lastPractisedFor(UUID userId, Collection<UUID> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return attemptRepo.findLastPractisedAtByLesson(userId, lessonIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Instant) row[1]));
    }

    private DictationLesson requirePublished(UUID lessonId) {
        return lessonRepo.findById(lessonId).filter(lesson -> lesson.getStatus() == DictationLessonStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("DICTATION_LESSON_NOT_FOUND",
                        "No published dictation lesson with id %s".formatted(lessonId)));
    }
}
