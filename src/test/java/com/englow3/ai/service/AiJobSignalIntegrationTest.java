package com.englow3.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

@Import(AiJobSignalIntegrationTest.Configuration.class)
class AiJobSignalIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    AiJobQueue queue;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    Signals signals;
    private UUID learner;

    @BeforeEach
    void setup() {
        learner = new LearnerFixture(jdbc).learner();
        signals.count.set(0);
    }

    @Test
    void workerSignalArrivesOnlyAfterTheOwningTransactionCommits() {
        UUID target = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            queue.enqueueTutorReply(target, "{}", "signal:" + target, "v1", learner);
            assertThat(signals.count.get()).isZero();
        });
        assertThat(signals.count.get()).isEqualTo(1);
    }

    @Test
    void rollbackCannotWakeTheWorkerForAnUncommittedJob() {
        UUID target = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            queue.enqueueTutorReply(target, "{}", "signal:" + target, "v1", learner);
            status.setRollbackOnly();
        });
        assertThat(signals.count.get()).isZero();
        assertThat(jdbc.sql("select count(*) from ai_jobs where target_id=:id").param("id", target).query(Long.class)
                .single()).isZero();
    }

    @Test
    void repeatedDeliveryDoesNotEmitAnotherSignalOrSpendAnotherBudget() {
        UUID target = UUID.randomUUID();
        queue.enqueueSpeechAssessment(target, "{}", "signal:" + target, "v1", learner);
        queue.enqueueSpeechAssessment(target, "{}", "signal:" + target, "v1", learner);
        assertThat(signals.count.get()).isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        Signals signals() {
            return new Signals();
        }
    }

    static class Signals {
        final AtomicInteger count = new AtomicInteger();

        @TransactionalEventListener
        public void queued(AiJobQueued event) {
            count.incrementAndGet();
        }
    }
}
