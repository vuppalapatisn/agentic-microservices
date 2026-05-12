package com.amol.microservices.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private final Loki loki = new Loki();
    private final Prometheus prometheus = new Prometheus();

    public Loki getLoki() { return loki; }
    public Prometheus getPrometheus() { return prometheus; }

    public static class Loki {
        private String baseUrl;
        private int timeoutSeconds = 5;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Prometheus {
        private String baseUrl;
        private int timeoutSeconds = 5;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
