package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.englow3.ai.speaking.SpeechProperties;

class AiFoundationConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()

            .withBean(RestClient.Builder.class, RestClient::builder).withUserConfiguration(AiFoundationConfig.class)

            .withPropertyValues("app.ai.enabled=true", "app.ai.base-url=http://ai-service:8000",

                    "app.ai.internal-api-key=test-internal-key", "app.ai.worker.batch-size=7",

                    "app.ai.worker.lock-timeout=3m", "app.speech.enabled=true", "app.speech.locale=en-GB",

                    "app.speech.max-audio-bytes=2097152", "app.speech.upload-url-ttl=15m", "app.speech.retention=14d");

    @Nested
    class Success {

        @Test
        void registersAndBindsAiAndSpeechProperties() {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(AiProperties.class)
                        .hasSingleBean(SpeechProperties.class).hasSingleBean(RestClient.class);

                AiProperties ai = context.getBean(AiProperties.class);
                assertThat(ai.enabled()).isTrue();
                assertThat(ai.baseUrl()).isEqualTo("http://ai-service:8000");
                assertThat(ai.internalApiKey()).isEqualTo("test-internal-key");
                assertThat(ai.worker().batchSize()).isEqualTo(7);
                assertThat(ai.worker().lockTimeout()).isEqualTo(Duration.ofMinutes(3));

                SpeechProperties speech = context.getBean(SpeechProperties.class);
                assertThat(speech.enabled()).isTrue();
                assertThat(speech.locale()).isEqualTo("en-GB");
                assertThat(speech.maxAudioBytes()).isEqualTo(2_097_152);
                assertThat(speech.uploadUrlTtl()).isEqualTo(Duration.ofMinutes(15));
                assertThat(speech.retention()).isEqualTo(Duration.ofDays(14));
            });
        }

    }

}
