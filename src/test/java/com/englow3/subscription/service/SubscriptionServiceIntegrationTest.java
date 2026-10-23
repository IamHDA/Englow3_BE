package com.englow3.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.subscription.dto.PlanView;
import com.englow3.subscription.dto.SubscriptionView;
import com.englow3.support.PostgresIntegrationTest;

/** Plans and what an administrator can do with them, against the real table and its constraints. */
class SubscriptionServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private SubscriptionService subscriptions;

    @Autowired
    private JdbcClient jdbc;

    private UUID authId;
    private UUID userId;
    private String email;

    @BeforeEach
    void signUp() {
        authId = UUID.randomUUID();
        email = "plan-%s@example.com".formatted(authId);
        jdbc.sql("insert into auth.users (id, email) values (:id, :email)").param("id", authId).param("email", email)
                .update();
        userId = jdbc.sql("select id from users where auth_provider_id = :a").param("a", authId).query(UUID.class)
                .single();
        Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject(authId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.sql("delete from users where id = :u").param("u", userId).update();
        jdbc.sql("delete from auth.users where id = :a").param("a", authId).update();
    }

    @Test
    void aLearnerWithNoRowIsOnTheFreePlan() {
        SubscriptionView view = subscriptions.mine();

        assertThat(view.plan()).isEqualTo("FREE");
        assertThat(view.currentPeriodEnd()).isNull();
    }

    @Test
    void grantingMakesThePlanPaidUntilTheEndOfThePeriod() {
        subscriptions.grantPaid(email, "MONTHLY", 30);

        SubscriptionView view = subscriptions.mine();
        assertThat(view.plan()).isEqualTo("PRO");
        assertThat(view.billingPeriod()).isEqualTo("MONTHLY");
        assertThat(Duration.between(Instant.now(), view.currentPeriodEnd()).toDays()).isBetween(29L, 30L);
        assertThat(view.aiDailyRequestLimit()).isGreaterThanOrEqualTo(300);
    }

    /** A month granted to someone with three weeks left gives seven weeks, not one - they paid for both. */
    @Test
    void grantingAgainAddsToWhatIsLeft() {
        subscriptions.grantPaid(email, "MONTHLY", 30);
        subscriptions.grantPaid(email, "MONTHLY", 30);

        assertThat(Duration.between(Instant.now(), subscriptions.mine().currentPeriodEnd()).toDays())
                .isBetween(59L, 60L);
    }

    @Test
    void anExpiredPlanReadsAsFreeWithoutAnyoneClearingIt() {
        subscriptions.grantPaid(email, "MONTHLY", 30);
        jdbc.sql("update user_subscriptions set current_period_end = now() - interval '1 minute' where user_id = :u")
                .param("u", userId).update();

        assertThat(subscriptions.mine().plan()).isEqualTo("FREE");
    }

    @Test
    void revokingReturnsToFreeAtOnce() {
        subscriptions.grantPaid(email, "YEARLY", 365);

        subscriptions.revoke(email);

        assertThat(subscriptions.mine().plan()).isEqualTo("FREE");
    }

    @Test
    void refusesAnUnknownEmailAndNonsenseTerms() {
        assertThatThrownBy(() -> subscriptions.grantPaid("nobody@example.com", "MONTHLY", 30))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> subscriptions.grantPaid(email, "WEEKLY", 30)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> subscriptions.grantPaid(email, "MONTHLY", 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> subscriptions.grantPaid(email, "MONTHLY", 100000))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theCatalogueListsFreeBeforePaidWithTheAllowanceOfEach() {
        List<PlanView> plans = subscriptions.plans();

        assertThat(plans).extracting(PlanView::code).containsExactly("FREE", "PRO");
        assertThat(plans.get(0).priceVndMonthly()).isZero();
        assertThat(plans.get(1).aiDailyRequestLimit()).isGreaterThanOrEqualTo(plans.get(0).aiDailyRequestLimit());
    }
}
