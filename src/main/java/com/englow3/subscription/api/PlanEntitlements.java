package com.englow3.subscription.api;

import java.util.UUID;

/**
 * What a learner's plan entitles them to, asked by the modules that spend something the plan limits.
 * <p>
 * Deliberately two small questions rather than a "plan" object handed around: a module that needs to know "may this
 * learner make another AI call" should not learn what plans exist, so adding a plan or a limit changes this module and
 * not every caller.
 */
public interface PlanEntitlements {

    /** Whether a paid plan is in force right now. An expired one counts as free without anyone having to clear it. */
    boolean hasPaidPlan(UUID userId);

    /** The AI requests per day a paid plan allows. The free figure is the AI module's own setting. */
    int paidAiDailyRequestLimit();
}
