package com.englow3.shared.observability;

import java.lang.management.ManagementFactory;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** Content-free, stable startup timing. Detailed bean/pool diagnostics remain ADMIN-only. */
@Component
public class StartupInfoContributor implements InfoContributor, ApplicationListener<ApplicationReadyEvent> {
    private volatile Timing timing;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getTimeTaken() != null)
            timing = new Timing(event.getTimeTaken().toMillis() / 1000.0,
                    ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
    }

    @Override
    public void contribute(Info.Builder builder) {
        var current = timing;
        if (current != null)
            builder.withDetail("startup", current);
    }

    public record Timing(double readySeconds, double processUptimeAtReadySeconds) {
    }
}
