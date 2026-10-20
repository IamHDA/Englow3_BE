package com.englow3.shared.observability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

class StartupInfoContributorTest {
    @Test
    void doesNotClaimReadyBeforeTheApplicationReadyEvent() {
        var contributor = new StartupInfoContributor();
        var builder = new Info.Builder();
        contributor.contribute(builder);
        assertThat(builder.build().getDetails()).doesNotContainKey("startup");
        contributor.onApplicationEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                mock(ConfigurableApplicationContext.class), Duration.ofMillis(12345)));
        builder = new Info.Builder();
        contributor.contribute(builder);
        assertThat(builder.build().getDetails().get("startup")).isInstanceOfSatisfying(
                StartupInfoContributor.Timing.class, timing -> assertThat(timing.readySeconds()).isEqualTo(12.345));
    }
}
