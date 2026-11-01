package com.englow3.progress.entity;

/** What kind of work a daily-path node asks for. Each value maps to a real practice screen. */
public enum DailyTaskKind {

    /** Cards the spaced-repetition schedule says are due. */
    FLASHCARD_REVIEW,

    /** A dictation lesson with sentences not yet cleared. */
    DICTATION,

    /** A published quiz the learner has not passed yet. */
    QUIZ,

    WRITING, SPEAKING,

    /** A pronunciation prompt scheduled by the learner's roadmap. */
    PRONUNCIATION,

    /** The test closing the current roadmap stage; the target is the milestone. */
    MILESTONE
}
