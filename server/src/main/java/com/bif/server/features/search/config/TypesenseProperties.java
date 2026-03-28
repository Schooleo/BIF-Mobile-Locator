package com.bif.server.features.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "typesense")
public class TypesenseProperties {

    private boolean enabled = false;
    private String protocol = "http";
    private String host = "localhost";
    private int port = 8108;
    private String apiKey = "";
    private String placesCollection = "places";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private boolean bootstrapReindexOnStartup = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPlacesCollection() {
        return placesCollection;
    }

    public void setPlacesCollection(String placesCollection) {
        this.placesCollection = placesCollection;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isBootstrapReindexOnStartup() {
        return bootstrapReindexOnStartup;
    }

    public void setBootstrapReindexOnStartup(boolean bootstrapReindexOnStartup) {
        this.bootstrapReindexOnStartup = bootstrapReindexOnStartup;
    }
}
