package com.englow3.progress.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.englow3.dictation.api.DictationCompletionPolicy;
import com.englow3.progress.dto.result.DailyPathResult;
import com.englow3.progress.entity.DailyTaskKind;
import com.englow3.progress.helper.DailyPlan;
import com.englow3.progress.helper.DailyQuests;
import com.englow3.progress.helper.ExperiencePoints;
import com.englow3.progress.helper.StudyStreak;
import com.englow3.progress.query.DailyPathQuery;
import com.englow3.progress.service.DailyPathService;
import com.englow3.shared.persistence.ParallelReads;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the daily path from what the learner has actually done. Read-only: this screen writes nothing, awards
 * nothing, and stores no plan. Asking for it twice gives the same answer because it is a view over the activity tables,
 * which is also why it cannot be "out of date".
 */
@Service
@RequiredArgsConstructor
public class DailyPathServiceImpl implements DailyPathService {

    /**
     * How many of each kind of outstanding work reach the roadmap. Capped low on purpose - a list of everything the
     * learner has not finished is a backlog, and a backlog is not a plan for one day.
     */
    private static final int TASKS_PER_KIND = 3;

    /** As far back as a streak can reach. Bounded so one learner's long history is not walked on every page load. */
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final DailyPathQuery pathQuery;
    private final DictationCompletionPolicy dictationCompletionPolicy;
    private final UserDirectory userDirectory;
    private final ParallelReads reads;
    private final Clock clock;
    private final StudyCalendar calendar;

    /**
     * Not transactional: the nine reads below are independent and run side by side through {@link ParallelReads}, each
     * on its own connection. In sequence they were most of two seconds of round trips.
     */
    public DailyPathResult dailyPath() {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        Instant startOfToday = calendar.startOfToday();

        var studyDaysRead = reads
                .fork(() -> pathQuery.studyDays(userId, now.minus(STREAK_LOOKBACK_DAYS, ChronoUnit.DAYS)));
        // Points are lifetime, so the window starts at the epoch rather than at the streak lookback: a learner who
        // took three months off has not un-earned the work they did before it.
        var totalsRead = reads.fork(() -> pathQuery.activityTotals(userId, Instant.EPOCH));
        var setsToday = reads.fork(() -> pathQuery.setsStudiedSince(userId, startOfToday));
        var lessonsToday = reads.fork(() -> pathQuery.lessonsPractisedSince(userId, startOfToday));
        var quizzesToday = reads.fork(() -> pathQuery.quizzesAttemptedSince(userId, startOfToday));
        var dueSets = reads.fork(() -> pathQuery.dueSets(userId, now, TASKS_PER_KIND));
        var unfinishedLessons = reads.fork(() -> pathQuery.unfinishedLessons(userId,
                dictationCompletionPolicy.completionThreshold(), TASKS_PER_KIND));
        var unpassedQuizzes = reads.fork(() -> pathQuery.unpassedQuizzes(userId, TASKS_PER_KIND));
        var questCounts = reads.fork(() -> pathQuery.questCounts(userId, startOfToday, now));

        List<LocalDate> studyDays = studyDaysRead.get();
        DailyPathQuery.ActivityTotals totals = totalsRead.get();
        long totalXp = ExperiencePoints.totalXp(new ExperiencePoints.Activity(totals.flashcardReviews(),
                totals.dictationSentences(), totals.quizAttempts(), totals.examAttempts()));
        ExperiencePoints.Level level = ExperiencePoints.levelFor(totalXp);

        return new DailyPathResult(StudyStreak.count(studyDays, today), level.totalXp(), level.level(),
                level.xpIntoLevel(), level.levelCostXp(), nodes(setsToday.get(), lessonsToday.get(), quizzesToday.get(),
                        dueSets.get(), unfinishedLessons.get(), unpassedQuizzes.get()),
                quests(questCounts.get(), studyDays, today));
    }

    private static List<DailyPlan.Node> nodes(List<DailyPathQuery.StudiedSet> setsToday,
            List<DailyPathQuery.PractisedLesson> lessonsToday, List<DailyPathQuery.AttemptedQuiz> quizzesToday,
            List<DailyPathQuery.DueSet> dueSets, List<DailyPathQuery.PendingLesson> unfinishedLessons,
            List<DailyPathQuery.PendingQuiz> unpassedQuizzes) {
        Map<UUID, Long> cardsPerSetToday = setsToday.stream().collect(
                Collectors.toMap(DailyPathQuery.StudiedSet::setId, DailyPathQuery.StudiedSet::cardCount, Long::sum));
        Map<UUID, Long> sentencesPerLessonToday = lessonsToday.stream().collect(Collectors.toMap(
                DailyPathQuery.PractisedLesson::lessonId, DailyPathQuery.PractisedLesson::sentenceCount, Long::sum));

        List<DailyPlan.Finished> finished = new ArrayList<>();
        setsToday.forEach(set -> finished.add(new DailyPlan.Finished(DailyTaskKind.FLASHCARD_REVIEW, set.setId(),
                set.name(), set.cardCount(), set.recallPercent())));
        lessonsToday.forEach(lesson -> finished.add(new DailyPlan.Finished(DailyTaskKind.DICTATION, lesson.lessonId(),
                lesson.title(), lesson.sentenceCount(), lesson.accuracyPercent())));
        // One row per attempt would list the same quiz three times for someone retaking it; the best of today reads
        // as "you did this today, and this is how it went".
        quizzesToday.stream()
                .collect(Collectors.toMap(DailyPathQuery.AttemptedQuiz::quizId, Function.identity(),
                        (left, right) -> score(left) >= score(right) ? left : right, java.util.LinkedHashMap::new))
                .values().forEach(quiz -> finished.add(new DailyPlan.Finished(DailyTaskKind.QUIZ, quiz.quizId(),
                        quiz.title(), 1, quiz.scorePercent())));

        List<DailyPlan.Candidate> candidates = new ArrayList<>();
        dueSets.forEach(set -> candidates.add(new DailyPlan.Candidate(DailyTaskKind.FLASHCARD_REVIEW, set.setId(),
                set.name(), set.dueCount(), cardsPerSetToday.getOrDefault(set.setId(), 0L), set.completionPercent())));
        unfinishedLessons.forEach(lesson -> candidates.add(new DailyPlan.Candidate(DailyTaskKind.DICTATION,
                lesson.lessonId(), lesson.title(), lesson.remainingSentences(),
                sentencesPerLessonToday.getOrDefault(lesson.lessonId(), 0L), lesson.completionPercent())));
        unpassedQuizzes.forEach(quiz -> candidates.add(new DailyPlan.Candidate(DailyTaskKind.QUIZ, quiz.quizId(),
                quiz.title(), quiz.questionCount(), 0, quiz.bestScorePercent())));

        return DailyPlan.build(finished, candidates);
    }

    private static List<DailyQuests.Quest> quests(DailyPathQuery.QuestCounts counts, List<LocalDate> studyDays,
            LocalDate today) {
        // Counted from the list already fetched for the streak rather than with another query.
        LocalDate weekStart = today.minusDays(DailyQuests.WEEK_DAYS - 1L);
        long studyDaysThisWeek = studyDays.stream().filter(day -> !day.isBefore(weekStart)).count();

        return DailyQuests.forToday(new DailyQuests.TodayActivity(counts.cardsReviewedToday(), counts.cardsDueNow(),
                counts.quizzesPassedToday(), counts.sentencesTypedToday(), studyDaysThisWeek));
    }

    private static int score(DailyPathQuery.AttemptedQuiz quiz) {
        return quiz.scorePercent() == null ? -1 : quiz.scorePercent();
    }
}
