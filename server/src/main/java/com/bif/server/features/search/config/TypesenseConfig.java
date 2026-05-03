package com.bif.server.features.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.typesense.api.Client;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class TypesenseConfig {

    private static final String DEFAULT_PROTOCOL = "http";

    @Bean
    public Client typesenseClient(TypesenseProperties properties) {
        String protocol = normalizeProtocol(properties.getProtocol());
        String host = safe(properties.getHost(), "localhost");
        String port = Integer.toString(properties.getPort());

        Duration connectionTimeout = Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 100));
        Duration readTimeout = Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 100));
        String apiKey = properties.getApiKey() == null ? "" : properties.getApiKey();

        List<Node> nodes = List.of(new Node(protocol, host, port));
        org.typesense.api.Configuration configuration =
            new org.typesense.api.Configuration(nodes, connectionTimeout, readTimeout, apiKey);

        return new Client(configuration);
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("typesense-rating-sync-");
        executor.initialize();
        return executor;
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeProtocol(String value) {
        String protocol = safe(value, DEFAULT_PROTOCOL).toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            return DEFAULT_PROTOCOL;
        }
        return protocol;
    }
}
