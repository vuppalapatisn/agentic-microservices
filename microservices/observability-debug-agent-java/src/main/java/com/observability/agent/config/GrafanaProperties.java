package com.observability.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "observability.grafana")
public class GrafanaProperties {

    private String baseUrl = "http://localhost:3000";
    private String apiBaseUrl = "http://grafana.observability.svc.cluster.local:3000";
    private String lokiDatasourceUid = "loki";
    private String dashboardUid = "ecommerce-observability";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String v) { this.apiBaseUrl = v; }

    public String getLokiDatasourceUid() { return lokiDatasourceUid; }
    public void setLokiDatasourceUid(String v) { this.lokiDatasourceUid = v; }

    public String getDashboardUid() { return dashboardUid; }
    public void setDashboardUid(String v) { this.dashboardUid = v; }
}
