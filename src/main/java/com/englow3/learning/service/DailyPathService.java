package com.englow3.learning.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.result.DailyPathResult;
import com.englow3.learning.entity.DailyTaskKind;
import com.englow3.learning.query.DailyPathQuery;
import com.englow3.user.service.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Assembles the daily path from what the learner has actually done. Read-only: this screen writes nothing, awards
 * nothing, and stores no plan. Asking for it twice gives the same answer because it is a view over the activity tables,
 * which is also why it cannot be "out of date".
 */
@Service
@RequiredArgsConstructor
public class DailyPathService {

    /** Must match {@code DictationService}: two definitions of "cleared" would leave a finished lesson on the list. */
    private static final BigDecimal DICTATION_COMPLETION_THRESHOLD = BigDecimal.valueOf(80);

    /**
     * How many of each kind of outstanding work reach the roadmap. Capped low on purpose - a list of everything the
     * learner has not finished is a backlog, and a backlog is not a plan for one day.
     */
    private static final int TASKS_PER_KIND = 3;

    /** As far back as a streak can reach. Bounded so one learner's long history is not walked on every page load. */
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final DailyPathQuery pathQuery;
    private final UserDirectory userDirectory;

    @Transactional(readOnly = true)
    public DailyPathResult dailyPath() {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<LocalDate> studyDays = pathQuery.studyDays(userId, now.minus(STREAK_LOOKBACK_DAYS, ChronoUnit.DAYS));

        // Points are lifetime, so the window starts at the epoch rather than at the streak lookback: a learner who
        // took three months off has not un-earned the work they did before it.
        DailyPathQuery.ActivityTotals totals = pathQuery.activityTotals(userId, Instant.EPOCH);
        long totalXp = ExperiencePoints.totalXp(new ExperiencePoints.Activity(totals.flashcardReviews(),
                totals.dictationSentences(), totals.quizAttempts(), totals.examAttempts()));
        ExperiencePoints.Level level = ExperiencePoints.levelFor(totalXp);

        return new DailyPathResult(StudyStreak.count(studyDays, today), level.totalXp(), level.level(),
                level.xpIntoLevel(), level.levelCostXp(), nodes(userId, now, startOfToday),
                quests(userId, now, startOfToday, studyDays, today));
    }

    private List<DailyPlan.Node> nodes(UUID userId, Instant now, Instant startOfToday) {
        List<DailyPathQuery.StudiedSet> setsToday = pathQuery.setsStudiedSince(userId, startOfToday);
        List<DailyPathQuery.PractisedLesson> lessonsToday = pathQuery.lessonsPractisedSince(userId, startOfToday);
        List<DailyPathQuery.AttemptedQuiz> quizzesToday = pathQuery.quizzesAttemptedSince(userId, startOfToday);

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
        pathQuery.dueSets(userId, now, TASKS_PER_KIND).forEach(
                set -> candidates.add(new DailyPlan.Candidate(DailyTaskKind.FLASHCARD_REVIEW, set.setId(), set.name(),
                        set.dueCount(), cardsPerSetToday.getOrDefault(set.setId(), 0L), set.completionPercent())));
        pathQuery.unfinishedLessons(userId, DICTATION_COMPLETION_THRESHOLD, TASKS_PER_KIND)
                .forEach(lesson -> candidates.add(new DailyPlan.Candidate(DailyTaskKind.DICTATION, lesson.lessonId(),
                        lesson.title(), lesson.remainingSentences(),
                        sentencesPerLessonToday.getOrDefault(lesson.lessonId(), 0L), lesson.completionPercent())));
        pathQuery.unpassedQuizzes(userId, TASKS_PER_KIND)
                .forEach(quiz -> candidates.add(new DailyPlan.Candidate(DailyTaskKind.QUIZ, quiz.quizId(), quiz.title(),
                        quiz.questionCount(), 0, quiz.bestScorePercent())));

        return DailyPlan.build(finished, candidates);
    }

    private List<DailyQuests.Quest> quests(UUID userId, Instant now, Instant startOfToday, List<LocalDate> studyDays,
            LocalDate today) {
        // Counted from the list already fetched for the streak rather than with another query.
        LocalDate weekStart = today.minusDays(DailyQuests.WEEK_DAYS - 1L);
        long studyDaysThisWeek = studyDays.stream().filter(day -> !day.isBefore(weekStart)).count();

        return DailyQuests.forToday(new DailyQuests.TodayActivity(pathQuery.cardsReviewedSince(userId, startOfToday),
                pathQuery.cardsDue(userId, now), pathQuery.quizzesPassedSince(userId, startOfToday),
                pathQuery.sentencesTypedSince(userId, startOfToday), studyDaysThisWeek));
    }

    private static int score(DailyPathQuery.AttemptedQuiz quiz) {
        return quiz.scorePercent() == null ? -1 : quiz.scorePercent();
    }
}
