package com.englow3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection only. Which bucket a file goes in belongs to the module that owns the file - see {@code app.storage}. */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(String endpoint, String region, String accessKey, String secretKey) {
}
