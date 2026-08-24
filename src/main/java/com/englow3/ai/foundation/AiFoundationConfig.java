package com.englow3.ai.foundation;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.englow3.ai.speaking.SpeechProperties;

@Configuration
@EnableConfigurationProperties({ AiProperties.class, SpeechProperties.class })
class AiFoundationConfig {

    @Bean
    RestClient aiServiceRestClient(AiProperties properties, RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder configured = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory);
        if (!properties.internalApiKey().isBlank()) {
            configured.defaultHeader("X-Internal-API-Key", properties.internalApiKey());
        }
        return configured.build();
    }
}
