package com.englow3.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * A single S3-compatible client/presigner shared by every module. Object key naming and bucket choice per file type
 * belong to the module that owns the file, not here.
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class StorageConfig {

    @Bean
    S3Client s3Client(S3Properties properties) {
        StaticCredentialsProvider credentials = credentials(properties);
        var builder = S3Client.builder().region(Region.of(properties.region())).credentialsProvider(credentials);
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(S3Properties properties) {
        StaticCredentialsProvider credentials = credentials(properties);
        var builder = S3Presigner.builder().region(Region.of(properties.region())).credentialsProvider(credentials);
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials(S3Properties properties) {
        return StaticCredentialsProvider
                .create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
