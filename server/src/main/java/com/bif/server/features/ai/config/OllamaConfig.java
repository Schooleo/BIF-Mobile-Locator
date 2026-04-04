package com.bif.server.features.ai.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

    @Bean("ollamaHttpClient")
    public HttpClient ollamaHttpClient(OllamaProperties ollamaProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(ollamaProperties.getTimeoutMs()))
                .build();
    }
}
