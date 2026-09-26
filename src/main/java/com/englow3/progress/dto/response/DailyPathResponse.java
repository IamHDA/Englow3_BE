package com.englow3.progress.dto.response;

import java.util.List;
import java.util.UUID;

import com.englow3.progress.dto.result.DailyPathResult;
import com.englow3.progress.entity.DailyQuestKind;
import com.englow3.progress.entity.DailyTaskKind;
import com.englow3.progress.entity.DailyTaskStatus;

public record DailyPathResponse(int streakDays, long totalXp, int level, long xpIntoLevel, long levelCostXp,
        List<DailyTaskResponse> tasks, List<DailyQuestResponse> quests) {

    public static DailyPathResponse from(DailyPathResult result) {
        return new DailyPathResponse(result.streakDays(), result.totalXp(), result.level(), result.xpIntoLevel(),
                result.levelCostXp(),
                result.nodes().stream()
                        .map(node -> new DailyTaskResponse(node.kind(), node.status(), node.targetId(), node.title(),
                                node.order(), node.unitsRemaining(), node.unitsDoneToday(), node.completionPercent(),
                                node.xpReward()))
                        .toList(),
                result.quests().stream().map(quest -> new DailyQuestResponse(quest.kind(), quest.progress(),
                        quest.target(), quest.completed())).toList());
    }

    /**
     * One stop on the roadmap.
     *
     * @param targetId
     *            the set, lesson or quiz to open - the client builds the link from this and the kind
     * @param unitsRemaining
     *            cards due, sentences left, or questions in the quiz
     * @param unitsDoneToday
     *            how much of this one the learner has already done today
     * @param completionPercent
     *            how far through it they are overall, or null if they have never opened it
     * @param xpReward
     *            what finishing it pays out, computed from the same weights the counter uses
     */
    public record DailyTaskResponse(DailyTaskKind kind, DailyTaskStatus status, UUID targetId, String title, int order,
            long unitsRemaining, long unitsDoneToday, Integer completionPercent, long xpReward) {
    }

    /**
     * {@code completed} is sent rather than left to the client so both sides agree on the edge where they are equal.
     */
    public record DailyQuestResponse(DailyQuestKind kind, long progress, long target, boolean completed) {
    }
}
