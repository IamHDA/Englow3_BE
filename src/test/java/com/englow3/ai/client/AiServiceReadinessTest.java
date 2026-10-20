package com.englow3.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServiceReadinessTest {
    private final AtomicLong now = new AtomicLong();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://adapter.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AiServiceReadiness readiness = new AiServiceReadiness(builder.build(), now::get);

    @Test
    void concurrentWorkerLanesShareOneProbeAndPositiveCacheExpires() throws Exception {
        server.expect(requestTo("http://adapter.test/health/live"))
                .andRespond(withSuccess("{\"status\":\"up\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://adapter.test/health/live")).andRespond(withServiceUnavailable());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tutor = executor.submit(readiness::isLive);
            var grading = executor.submit(readiness::isLive);
            assertThat(tutor.get(3, TimeUnit.SECONDS)).isTrue();
            assertThat(grading.get(3, TimeUnit.SECONDS)).isTrue();
        }
        now.set(Duration.ofSeconds(5).toNanos());
        assertThat(readiness.isLive()).isFalse();
        server.verify();
    }

    @Test
    void unavailableAdapterIsRecheckedSoonWithoutSpammingItsWakeup() {
        server.expect(requestTo("http://adapter.test/health/live")).andRespond(withServiceUnavailable());
        server.expect(requestTo("http://adapter.test/health/live"))
                .andRespond(withSuccess("{\"status\":\"up\"}", MediaType.APPLICATION_JSON));
        assertThat(readiness.isLive()).isFalse();
        assertThat(readiness.isLive()).isFalse();
        now.set(Duration.ofSeconds(1).toNanos());
        assertThat(readiness.isLive()).isTrue();
        server.verify();
    }

    @Test
    void malformedLivenessResponseCannotOpenTheProviderGate() {
        server.expect(requestTo("http://adapter.test/health/live"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThat(readiness.isLive()).isFalse();
        assertThat(readiness.isLive()).isFalse();
        server.verify();
    }
}
