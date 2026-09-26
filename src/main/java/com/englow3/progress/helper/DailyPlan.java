package com.englow3.progress.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.englow3.progress.entity.DailyTaskKind;
import com.englow3.progress.entity.DailyTaskStatus;

/**
 * Turns real outstanding work and today's finished work into the ordered list the roadmap draws. Pure, because the
 * awkward cases are all about which of two views of the same set wins, and that deserves to be pinned down without a
 * database.
 * <p>
 * Nothing here invents a task. Every node points at a set, lesson or quiz that exists, and the figures on it are counts
 * of rows.
 */
public final class DailyPlan {

    private DailyPlan() {
    }

    /**
     * Work the learner still owes.
     *
     * @param unitsRemaining
     *            cards due, sentences not cleared, or questions in an unpassed quiz
     * @param unitsDoneToday
     *            how much of it they have already got through today
     * @param completionPercent
     *            how far through the whole set/lesson/quiz they are, or null when they have never touched it
     */
    public record Candidate(DailyTaskKind kind, UUID targetId, String title, long unitsRemaining, long unitsDoneToday,
            Integer completionPercent) {
    }

    /** Work finished today. */
    public record Finished(DailyTaskKind kind, UUID targetId, String title, long unitsDoneToday,
            Integer completionPercent) {
    }

    public record Node(DailyTaskKind kind, DailyTaskStatus status, UUID targetId, String title, int order,
            long unitsRemaining, long unitsDoneToday, Integer completionPercent, long xpReward) {
    }

    /**
     * Scheduled work first. Spaced repetition is the only part of this product with a deadline - a card reviewed a week
     * late is a card the schedule has lost - while a quiz or a dictation lesson is equally useful whenever it is done.
     */
    private static final List<DailyTaskKind> KIND_ORDER = List.of(DailyTaskKind.FLASHCARD_REVIEW,
            DailyTaskKind.DICTATION, DailyTaskKind.QUIZ);

    /**
     * @param finished
     *            things the learner worked on today
     * @param candidates
     *            things still outstanding
     */
    public static List<Node> build(List<Finished> finished, List<Candidate> candidates) {
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(candidate -> KIND_ORDER.indexOf(candidate.kind())));

        Set<String> outstanding = new HashSet<>();
        for (Candidate candidate : ordered) {
            outstanding.add(key(candidate.kind(), candidate.targetId()));
        }

        List<Node> nodes = new ArrayList<>();
        int order = 1;

        // A set worked on today that still has cards due is not finished, and showing it twice - once as a tick, once
        // as the next thing to do - would say both. The outstanding entry wins because it carries what is left.
        for (Finished done : finished) {
            if (outstanding.contains(key(done.kind(), done.targetId()))) {
                continue;
            }
            nodes.add(new Node(done.kind(), DailyTaskStatus.COMPLETED, done.targetId(), done.title(), order++, 0,
                    done.unitsDoneToday(), done.completionPercent(), 0));
        }

        for (int i = 0; i < ordered.size(); i++) {
            Candidate candidate = ordered.get(i);
            DailyTaskStatus status = i == 0 ? DailyTaskStatus.CURRENT : DailyTaskStatus.UPCOMING;
            nodes.add(new Node(candidate.kind(), status, candidate.targetId(), candidate.title(), order++,
                    candidate.unitsRemaining(), candidate.unitsDoneToday(), candidate.completionPercent(),
                    xpFor(candidate.kind(), candidate.unitsRemaining())));
        }

        return nodes;
    }

    /**
     * What finishing the node actually pays out. Reads the same constants {@link ExperiencePoints} counts with, so the
     * reward a node advertises cannot drift from the reward the learner gets.
     */
    static long xpFor(DailyTaskKind kind, long unitsRemaining) {
        return switch (kind) {
            case FLASHCARD_REVIEW -> unitsRemaining * ExperiencePoints.FLASHCARD_REVIEW_XP;
            case DICTATION -> unitsRemaining * ExperiencePoints.DICTATION_SENTENCE_XP;
            // Per attempt, not per question: a long quiz is one submission.
            case QUIZ -> ExperiencePoints.QUIZ_ATTEMPT_XP;
        };
    }

    private static String key(DailyTaskKind kind, UUID targetId) {
        return kind.name() + ":" + targetId;
    }
}
