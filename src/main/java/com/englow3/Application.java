package com.englow3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        var application = new SpringApplication(Application.class);
        application.setApplicationStartup(
                new org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup(2048));
        application.run(args);
    }
}
