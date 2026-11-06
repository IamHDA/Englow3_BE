package com.englow3.roadmap.service;

import java.util.List;
import java.util.UUID;

/**
 * Applies new grades to level checks and roadmaps without anyone having to open a page. Called when the exam or
 * assessment module announces a grade, and by the periodic sweep that catches anything an announcement missed (the
 * process stopped between commit and delivery, a listener failed).
 * <p>
 * Each method runs in a transaction of its own: it is called after the grading transaction has committed, and one
 * learner's failure must not undo another's update in the same sweep.
 */
public interface RoadmapResultSync {

    /** A full sitting of {@code examId} was scored: level checks on that paper, and a mock milestone, may move on. */
    void examSittingScored(UUID userId, UUID examId);

    /** An IELTS full test of the learner's has its overall band: an IELTS mock milestone may move on. */
    void fullTestCompleted(UUID userId);

    /** A spoken answer to {@code taskId} was graded: level checks asking for that task may move on. */
    void speakingAnswerGraded(UUID userId, UUID taskId);

    void refreshLevelCheck(UUID levelCheckId);

    void refreshRoadmap(UUID roadmapId);

    /** A page of unfinished level checks recent enough to be worth looking at again, oldest first. */
    List<UUID> unfinishedLevelCheckIds(int page, int size);

    /** A page of active roadmaps, oldest first. */
    List<UUID> activeRoadmapIds(int page, int size);
}
