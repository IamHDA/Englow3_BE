package com.englow3.progress.entity;

/**
 * The daily goals the interface tracks. The kind travels instead of a sentence because the wording is interface copy in
 * two languages, and generating Vietnamese prose in a service is how the backend ends up owning half the interface.
 */
public enum DailyQuestKind {

    /** Clear the cards the schedule made due today. */
    REVIEW_DUE_CARDS,

    /** Reach the pass mark on any quiz today. */
    PASS_A_QUIZ,

    /** Transcribe a number of dictation sentences today. */
    TYPE_SENTENCES,

    /** Study on each of the last seven days. */
    PRACTISE_EVERY_DAY
}
