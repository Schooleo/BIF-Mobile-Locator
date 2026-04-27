package com.bif.server.features.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class EmailHttpClientConfig {

    private static final int BREVO_CONNECT_TIMEOUT_MS = 5000;
    private static final int BREVO_READ_TIMEOUT_MS = 10000;

    @Bean("emailRestTemplate")
    public RestTemplate emailRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(BREVO_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(BREVO_READ_TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-send-");
        executor.initialize();
        return executor;
    }
}
