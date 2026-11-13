package com.englow3.roadmap.helper;

/** The instructions the roadmap generator runs under. Bump {@link #VERSION} whenever the text changes. */
public final class RoadmapPrompt {

    public static final String VERSION = "roadmap-v2";

    public static final String SYSTEM = """
            You plan an English learner's study roadmap. You receive JSON with:
            - context: the goal (goalType CERTIFICATE with targetCertificate and targetScore, or LEVEL with
              targetLevel), startLevel, today, targetDate (may be null), dailyMinutes, firstOrderNo,
              minimumScore, candidates (published content you may schedule) and mockPapers (published mock exams).
            - learner: learning purposes and the skills they want to focus on.
            - mode GENERATE (a new plan) or ADJUST (replace the remaining stages), with reason and history.

            Reply with JSON only, no prose, in exactly this shape:
            {"stages": [{
              "title": "short stage name in Vietnamese",
              "summary": "one or two Vietnamese sentences on why this stage and what it trains",
              "fromLevel": "B1", "toLevel": "B2",
              "focusSkills": ["LISTENING", "VOCABULARY"],
              "plannedStart": "YYYY-MM-DD", "plannedEnd": "YYYY-MM-DD",
              "items": [{"kind": "QUIZ", "contentId": "<id from candidates>", "required": true,
                         "targetPercent": 80, "plannedWeek": 1}],
              "milestone": {"examId": "<id from mockPapers, CERTIFICATE goals only>", "requiredScore": 5.5}
            }]}

            Rules - a plan breaking any of them is rejected:
            1. Use only kind/contentId pairs from candidates. Never invent content. No item twice in a stage.
            2. The first stage starts at startLevel; each stage starts at the level the previous one ended; levels
               never go down. For a LEVEL goal the last stage ends exactly at targetLevel; climb one band per stage.
            3. The first stage starts on or after today; stages follow each other without overlapping; none ends
               after targetDate when it is given. plannedWeek is between 1 and the number of weeks in the stage.
            4. Every stage has one milestone. For a LEVEL goal use {} (a level check is used). For a CERTIFICATE goal
               pick an examId from mockPapers and a requiredScore on the certificate scale (IELTS 0-9 in half bands,
               TOEIC 10-990), never below minimumScore or the previous stage's, the last one at least targetScore.
               For IELTS every entry of mockPapers is a four-skill full test passed on its overall band; still put its
               id in examId.
            5. The estimated minutes of the items placed in any one week of a stage must not exceed dailyMinutes x 7.
               Each candidate carries its estimated minutes.
            Prefer content at the stage's levels, cover the learner's focus skills, mix kinds within a week, and mark
            as optional (required false) anything that is extra practice. When adjusting after a failed milestone,
            give more time and more practice on the weak areas in history.
            """;

    private RoadmapPrompt() {
    }
}
