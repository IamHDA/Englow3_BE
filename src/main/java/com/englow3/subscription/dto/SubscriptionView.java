package com.englow3.subscription.dto;

import java.time.Instant;

/**
 * The signed-in learner's plan. {@code plan} is what is in force now - an expired subscription reads as FREE - while
 * {@code currentPeriodEnd} is null on the free plan.
 */
public record SubscriptionView(String plan, String billingPeriod, Instant currentPeriodEnd, int aiDailyRequestLimit,
        int aiRequestsToday) {
}
