package com.englow3.ai.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.AiServiceReadiness;
import com.englow3.ai.service.AiJobWorkerQueue;

class AiWorkerConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withInitializer(context -> {
            // Load the actual application defaults, without inheriting this machine's deployment flags.
            var sources = context.getEnvironment().getPropertySources();
            sources.remove("systemEnvironment");
            sources.remove("systemProperties");
            context.getBeanFactory().setConversionService(
                    org.springframework.boot.convert.ApplicationConversionService.getSharedInstance());
            try {
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                        .forEach(sources::addLast);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }).withUserConfiguration(AiJobWorker.class).withBean(AiJobWorkerQueue.class, () -> mock(AiJobWorkerQueue.class))
                .withBean(AiServiceReadiness.class, () -> mock(AiServiceReadiness.class))
                .withBean(AiJobHandler.class, () -> {
                    var handler = mock(AiJobHandler.class);
                    when(handler.handles()).thenReturn("TUTOR_REPLY");
                    return handler;
                }).withPropertyValues("app.ai.enabled=true");
    }

    @Test
    void missingWorkerSettingsCannotStartAConsumerOnTheDeployedQueue() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AiJobWorker.class);
            assertThat(context.getEnvironment().getProperty("app.ai.queue-scope")).isEqualTo("local");
        });
    }

    @Test
    void anExplicitDeploymentCanEnableItsScopedWorker() {
        runner().withPropertyValues("AI_WORKER_ENABLED=true", "AI_QUEUE_SCOPE=testing").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiJobWorker.class);
            assertThat(context.getEnvironment().getProperty("app.ai.queue-scope")).isEqualTo("testing");
        });
    }
}
