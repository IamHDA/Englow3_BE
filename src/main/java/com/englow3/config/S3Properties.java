package com.englow3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(String endpoint, String region, String accessKey, String secretKey, String bucket) {
}
