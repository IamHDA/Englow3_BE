package com.englow3.dictation.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.dictation.dto.command.AddDictationSentencesCommand;
import com.englow3.dictation.dto.command.AddDictationSentencesCommand.NewSentence;
import com.englow3.dictation.dto.command.CreateDictationLessonCommand;
import com.englow3.dictation.dto.result.ContentReviewResult;
import com.englow3.dictation.dto.result.DictationLessonSummaryResult;
import com.englow3.dictation.entity.DictationLesson;
import com.englow3.dictation.entity.DictationLessonStatus;
import com.englow3.dictation.entity.DictationSentence;
import com.englow3.dictation.helper.DictationImport;
import com.englow3.dictation.helper.DictationScorer;
import com.englow3.dictation.repository.DictationLessonRepository;
import com.englow3.dictation.repository.DictationSentenceRepository;
import com.englow3.dictation.service.AdminDictationService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.dictation.dto.result.DictationImportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Authoring dictation lessons. Per-learner figures are zero here - an author is not practising their own lesson. */
@Service
@RequiredArgsConstructor
public class AdminDictationServiceImpl implements AdminDictationService {

    private final DictationLessonRepository lessonRepo;
    private final DictationSentenceRepository sentenceRepo;
    private final UserDirectory userDirectory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public DictationLessonSummaryResult create(CreateDictationLessonCommand command) {
        if (lessonRepo.existsBySlug(command.slug())) {
            throw new ConflictException("DICTATION_LESSON_SLUG_TAKEN",
                    "A lesson already uses the slug %s".formatted(command.slug()));
        }

        DictationLesson lesson = lessonRepo.save(DictationLesson.draft(command.slug(), command.title(), command.topic(),
                command.targetLevel(), userDirectory.requireCurrentUserId()));
        return summaryOf(lesson);
    }

    /**
     * Appends sentences. Allowed on a published lesson: a new line is simply unpractised, and nothing already recorded
     * is scored against the lesson as a whole.
     */
    @Transactional
    public DictationLessonSummaryResult addSentences(AddDictationSentencesCommand command) {
        DictationLesson lesson = requireLesson(command.lessonId());
        int nextOrderNo = Math.toIntExact(sentenceRepo.countByDictationLessonId(lesson.getId())) + 1;

        List<DictationSentence> sentences = new ArrayList<>();
        for (NewSentence source : command.sentences()) {
            // Word count comes from the scorer so the hint agrees with the marking.
            sentences.add(DictationSentence.of(lesson.getId(), nextOrderNo++, source.text(), source.translationVi(),
                    source.audioObjectKey(), source.audioDurationSeconds(), DictationScorer.words(source.text()).size(),
                    source.hintFirstLetters(), source.hintRevealWord(), source.hintPartialTranscript()));
        }
        sentenceRepo.saveAll(sentences);

        return summaryOf(lesson);
    }

    /** Reads a generated shadowing batch and says what it would do. Writes nothing. */
    @Transactional(readOnly = true)
    public DictationImportResult validateImport(String json) {
        return DictationImportResult.of(DictationImport.read(objectMapper, json), false);
    }

    /**
     * Turns a shadowing batch into draft lessons.
     * <p>
     * One lesson per clip, one sentence per segment, every sentence pointing at the clip's own recording and differing
     * only by where it starts. Draft, like every import: generated content is not reviewed content.
     * <p>
     * A clip whose slug is already taken is skipped rather than failing the batch - re-running an import should be
     * safe, and the alternative is an author deleting thirty lessons to retry one.
     */
    @Transactional
    public DictationImportResult importLessons(String json) {
        DictationImport.Report report = DictationImport.read(objectMapper, json);
        UUID authorId = userDirectory.requireCurrentUserId();
        List<DictationImport.Rejection> skipped = new ArrayList<>(report.rejections());
        List<DictationImport.Lesson> stored = new ArrayList<>();

        int index = 0;
        for (DictationImport.Lesson lesson : report.lessons()) {
            index++;
            if (lessonRepo.existsBySlug(lesson.slug())) {
                skipped.add(new DictationImport.Rejection(index, lesson.slug(), "Already imported"));
                continue;
            }
            store(lesson, authorId);
            stored.add(lesson);
        }

        return DictationImportResult.of(new DictationImport.Report(List.copyOf(stored), List.copyOf(skipped)), true);
    }

    private void store(DictationImport.Lesson lesson, UUID authorId) {
        DictationLesson saved = lessonRepo.save(
                DictationLesson.draft(lesson.slug(), lesson.title(), "shadowing", lesson.targetLevel(), authorId));

        List<DictationSentence> sentences = new ArrayList<>();
        int orderNo = 1;
        for (DictationImport.Segment segment : lesson.segments()) {
            sentences.add(DictationSentence.of(saved.getId(), orderNo++, segment.text(), null, lesson.audioObjectKey(),
                    lesson.durationSeconds(), DictationScorer.words(segment.text()).size(), null, null, null,
                    segment.startMs(), segment.endMs()));
        }
        sentenceRepo.saveAll(sentences);
    }

    /**
     * Every lesson, whatever its status - the catalogue shows published ones only, and an administrator with no way to
     * see a draft has no way to review one. Sentence counts arrive in one grouped query, not one per row.
     */
    @Transactional(readOnly = true)
    public Page<ContentReviewResult> searchForAuthoring(DictationLessonStatus status, String title, Pageable pageable) {
        Page<DictationLesson> page = lessonRepo.searchForAuthoring(status, title, pageable);
        Map<UUID, Long> counts = sentenceRepo
                .countByLessonIds(page.getContent().stream().map(DictationLesson::getId).toList());

        return page.map(lesson -> ContentReviewResult.of(lesson, counts.getOrDefault(lesson.getId(), 0L)));
    }

    @Transactional
    public ContentReviewResult publish(UUID lessonId) {
        DictationLesson lesson = requireLesson(lessonId);
        lesson.publish(sentenceRepo.countByDictationLessonId(lessonId), clock.instant());
        return reviewStateOf(lesson);
    }

    @Transactional
    public ContentReviewResult submitForReview(UUID lessonId) {
        DictationLesson lesson = requireLesson(lessonId);
        lesson.submitForReview(sentenceRepo.countByDictationLessonId(lessonId), clock.instant());

        return reviewStateOf(lesson);
    }

    /** The reviewer's id comes from the token, not the request - nobody credits an approval to someone else. */
    @Transactional
    public ContentReviewResult approve(UUID lessonId) {
        DictationLesson lesson = requireLesson(lessonId);
        lesson.approve(userDirectory.requireCurrentUserId(), sentenceRepo.countByDictationLessonId(lessonId),
                clock.instant());

        return reviewStateOf(lesson);
    }

    @Transactional
    public ContentReviewResult reject(UUID lessonId, String note) {
        DictationLesson lesson = requireLesson(lessonId);
        lesson.reject(userDirectory.requireCurrentUserId(), note, clock.instant());

        return reviewStateOf(lesson);
    }

    @Transactional
    public ContentReviewResult archive(UUID lessonId) {
        DictationLesson lesson = requireLesson(lessonId);
        lesson.archive();
        return reviewStateOf(lesson);
    }

    /**
     * The review state is what an authoring action changed, so it is what an authoring action returns - and unlike the
     * catalogue summary, it does not load every sentence to build a figure the author did not ask for.
     */
    private ContentReviewResult reviewStateOf(DictationLesson lesson) {
        return ContentReviewResult.of(lesson, sentenceRepo.countByDictationLessonId(lesson.getId()));
    }

    private DictationLessonSummaryResult summaryOf(DictationLesson lesson) {
        List<DictationSentence> sentences = sentenceRepo.findByDictationLessonIdOrderByOrderNo(lesson.getId());
        return DictationLessonSummaryResult.of(lesson, sentences.size(), 0L,
                sentences.stream().mapToInt(DictationSentence::getAudioDurationSeconds).sum(), null);
    }

    private DictationLesson requireLesson(UUID lessonId) {
        return lessonRepo.findById(lessonId).orElseThrow(() -> new NotFoundException("DICTATION_LESSON_NOT_FOUND",
                "No dictation lesson with id %s".formatted(lessonId)));
    }
}
