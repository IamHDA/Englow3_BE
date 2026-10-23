package com.englow3.subscription.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.subscription.api.PlanEntitlements;
import com.englow3.subscription.dto.PlanView;
import com.englow3.subscription.dto.SubscriptionView;
import com.englow3.subscription.service.SubscriptionService;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class SubscriptionServiceImpl implements SubscriptionService {

    private static final Set<String> PERIODS = Set.of("MONTHLY", "YEARLY");
    /** Ten years. A typo of an extra zero should not hand someone a plan for a thousand years. */
    private static final int MAX_GRANT_DAYS = 3650;

    private final JdbcClient jdbc;
    private final PlanEntitlements entitlements;
    private final AiJobQueue aiJobQueue;
    private final UserDirectory users;

    @Value("${app.plans.pro.price-vnd-monthly:99000}")
    private long proPriceMonthly;

    @Value("${app.plans.pro.price-vnd-yearly:990000}")
    private long proPriceYearly;

    @Override
    public List<PlanView> plans() {
        return List.of(new PlanView("FREE", 0, 0, aiJobQueue.dailyRequestLimit()),
                new PlanView("PRO", proPriceMonthly, proPriceYearly, entitlements.paidAiDailyRequestLimit()));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionView mine() {
        UUID userId = users.requireCurrentUserId();
        boolean paid = entitlements.hasPaidPlan(userId);
        var usage = aiJobQueue.usage(userId);
        if (!paid) {
            return new SubscriptionView("FREE", null, null, usage.limit(), usage.used());
        }
        var row = jdbc.sql("select billing_period, current_period_end from user_subscriptions where user_id = :u")
                .param("u", userId).query((rs, i) -> new PeriodRow(rs.getString(1), rs.getTimestamp(2).toInstant()))
                .single();
        return new SubscriptionView("PRO", row.billingPeriod(), row.end(), usage.limit(), usage.used());
    }

    @Override
    @Transactional
    public void grantPaid(String email, String billingPeriod, int days) {
        if (!PERIODS.contains(billingPeriod)) {
            throw new BadRequestException("SUBSCRIPTION_PERIOD_INVALID", "billingPeriod must be MONTHLY or YEARLY");
        }
        if (days < 1 || days > MAX_GRANT_DAYS) {
            throw new BadRequestException("SUBSCRIPTION_DAYS_INVALID",
                    "days must be between 1 and " + MAX_GRANT_DAYS);
        }
        UUID target = requireByEmail(email);
        jdbc.sql("""
                insert into user_subscriptions (user_id, plan, billing_period, current_period_end, source,
                                                granted_by_user_id)
                values (:u, 'PRO', :period,
                        greatest(now(), coalesce((select current_period_end from user_subscriptions
                                                   where user_id = :u and plan = 'PRO'), now()))
                          + make_interval(days => :days),
                        'MANUAL', :by)
                on conflict (user_id) do update
                   set plan = 'PRO', billing_period = excluded.billing_period,
                       current_period_end = excluded.current_period_end, source = 'MANUAL',
                       granted_by_user_id = excluded.granted_by_user_id, updated_at = now()
                """).param("u", target).param("period", billingPeriod).param("days", days)
                .param("by", users.requireCurrentUserId()).update();
    }

    @Override
    @Transactional
    public void revoke(String email) {
        jdbc.sql("delete from user_subscriptions where user_id = :u").param("u", requireByEmail(email)).update();
    }

    private record PeriodRow(String billingPeriod, Instant end) {
    }

    private UUID requireByEmail(String email) {
        Optional<UUID> id = jdbc.sql("select id from users where lower(email) = lower(:e)").param("e", email == null
                ? "" : email.trim()).query(UUID.class).optional();
        return id.orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No account has that email address"));
    }
}
