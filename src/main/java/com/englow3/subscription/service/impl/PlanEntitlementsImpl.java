package com.englow3.subscription.service.impl;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.subscription.api.PlanEntitlements;

import lombok.RequiredArgsConstructor;

/**
 * Kept apart from {@link SubscriptionServiceImpl}, which reads the AI module for usage figures: this class is what the
 * AI module reads, so folding the two together would make each wait on the other at start-up.
 */
@Component
@RequiredArgsConstructor
class PlanEntitlementsImpl implements PlanEntitlements {

    private final JdbcClient jdbc;

    @Value("${app.plans.pro.ai-daily-limit:300}")
    private int paidAiDailyRequestLimit;

    @Override
    public boolean hasPaidPlan(UUID userId) {
        // The database's clock, not the application's: the end of a period is a timestamp written by the same server
        // that compares it, so a skewed instance cannot extend a plan.
        return jdbc.sql("""
                select exists (select 1 from user_subscriptions
                                where user_id = :u and plan = 'PRO' and current_period_end > now())
                """).param("u", userId).query(Boolean.class).single();
    }

    @Override
    public int paidAiDailyRequestLimit() {
        return paidAiDailyRequestLimit;
    }
}
