package com.englow3.learning.entity;

/**
 * Where a node sits in today's plan.
 * <p>
 * There is deliberately no {@code LOCKED}. Nothing in this product gates one piece of content behind another - every
 * published set, lesson and quiz is open to every learner - so a locked node would be a padlock drawn over something
 * the learner could have done all along. {@link #UPCOMING} says "not started", which is true, instead of "you may not",
 * which is not.
 */
public enum DailyTaskStatus {

    /** Finished today, with nothing left outstanding on it. */
    COMPLETED,

    /** The one piece of work the plan suggests next. */
    CURRENT,

    /** Outstanding, and the learner may start it whenever they like. */
    UPCOMING
}
