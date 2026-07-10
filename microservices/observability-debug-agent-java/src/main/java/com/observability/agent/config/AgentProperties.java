package com.observability.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "observability.agent")
public class AgentProperties {

    private String baseUrl = "http://observability-server.observability.svc.cluster.local:8091";
    private int requestTimeoutSeconds = 10;
    private int startupValidationRetries = 30;
    private long startupValidationRetrySeconds = 2;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int v) { this.requestTimeoutSeconds = v; }

    public int getStartupValidationRetries() { return startupValidationRetries; }
    public void setStartupValidationRetries(int v) { this.startupValidationRetries = v; }

    public long getStartupValidationRetrySeconds() { return startupValidationRetrySeconds; }
    public void setStartupValidationRetrySeconds(long v) { this.startupValidationRetrySeconds = v; }
}
