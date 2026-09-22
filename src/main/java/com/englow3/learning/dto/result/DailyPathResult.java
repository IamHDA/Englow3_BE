package com.englow3.learning.dto.result;

import java.util.List;

import com.englow3.learning.service.DailyPlan;
import com.englow3.learning.service.DailyQuests;

/**
 * Everything the daily-path screen shows. One shape rather than four endpoints because the page draws the banner, the
 * roadmap and the quests together, and a learner should not watch three of them arrive separately.
 *
 * @param streakDays
 *            consecutive days with any practice, counted across every feature
 * @param totalXp
 *            derived from all recorded activity, never stored
 */
public record DailyPathResult(int streakDays, long totalXp, int level, long xpIntoLevel, long levelCostXp,
        List<DailyPlan.Node> nodes, List<DailyQuests.Quest> quests) {
}
