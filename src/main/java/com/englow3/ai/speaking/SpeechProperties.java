package com.englow3.ai.speaking;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.speech")
public record SpeechProperties(@DefaultValue("false") boolean enabled, @DefaultValue("en-US") String locale,
        @DefaultValue("10485760") long maxAudioBytes, @DefaultValue("10m") Duration uploadUrlTtl,
        @DefaultValue("30d") Duration retention) {
}
