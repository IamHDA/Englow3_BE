package com.englow3.user.service;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import jakarta.persistence.EntityManagerFactory;

class UserBootstrapIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    UserBootstrapService bootstrap;
    @Autowired
    UserService users;
    @Autowired
    OnboardingService onboarding;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    private UUID learner() {
        UUID id = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, id);
        return id;
    }

    @AfterEach
    void cleanup() {
        SignedIn.out();
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().setStatisticsEnabled(false);
    }

    @Test
    void preservesTheAccountAndOnboardingWithFewerDatabaseRoundTrips() {
        UUID user = learner();
        jdbc.sql(
                "insert into learner_profiles(id,user_id,current_level,target_certificate_type,target_score) values(:id,:user,'B1','IELTS',6.5)")
                .param("id", UUID.randomUUID()).param("user", user).update();
        jdbc.sql(
                "insert into user_learning_purposes(user_id,learning_purpose_id) select :user,id from learning_purposes where purpose_code='CERTIFICATE'")
                .param("user", user).update();
        jdbc.sql("insert into user_target_skills(user_id,skill) values(:user,'SPEAKING')").param("user", user).update();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var previousUser = users.me();
        var previousState = onboarding.currentState();
        long previousQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        var current = bootstrap.current();
        long currentQueries = statistics.getPrepareStatementCount();
        assertThat(current.user()).isEqualTo(previousUser);
        assertThat(current.onboardingState()).isEqualTo(previousState);
        assertThat(currentQueries).isLessThan(previousQueries).isLessThanOrEqualTo(3);
    }

    @Test
    void readingANewAccountNeverCreatesItsProfile() {
        UUID user = learner();
        var result = bootstrap.current();
        assertThat(result.user().id()).isEqualTo(user);
        assertThat(result.onboardingState().currentLevel()).isNull();
        assertThat(jdbc.sql("select count(*) from learner_profiles where user_id=:user").param("user", user)
                .query(Long.class).single()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "STAFF", "ADMIN" })
    void backOfficeAccountsDoNotLoadLearnerOnboarding(String role) {
        UUID user = learner();
        jdbc.sql("update users set role=:role where id=:user").param("role", role).param("user", user).update();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var current = bootstrap.current();
        assertThat(current.user().role().name()).isEqualTo(role);
        assertThat(current.onboardingState()).isNull();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void neverReusesAnotherAccountsSnapshot() {
        UUID first = learner();
        assertThat(bootstrap.current().user().id()).isEqualTo(first);
        UUID second = learner();
        assertThat(bootstrap.current().user().id()).isEqualTo(second).isNotEqualTo(first);
    }
}
