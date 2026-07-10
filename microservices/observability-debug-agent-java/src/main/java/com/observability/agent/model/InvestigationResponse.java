package com.observability.agent.model;

import java.util.List;

public class InvestigationResponse {

    private String investigationId;
    private String correlationId;
    private String summary;
    private String probableRootCause;
    private List<String> evidence;
    private String grafanaExploreUrl;
    private String grafanaDashboardUrl;

    public InvestigationResponse() {}

    public static Builder builder() { return new Builder(); }

    public String getInvestigationId() { return investigationId; }
    public String getCorrelationId() { return correlationId; }
    public String getSummary() { return summary; }
    public String getProbableRootCause() { return probableRootCause; }
    public List<String> getEvidence() { return evidence; }
    public String getGrafanaExploreUrl() { return grafanaExploreUrl; }
    public String getGrafanaDashboardUrl() { return grafanaDashboardUrl; }

    public static class Builder {
        private final InvestigationResponse r = new InvestigationResponse();

        public Builder investigationId(String v) { r.investigationId = v; return this; }
        public Builder correlationId(String v) { r.correlationId = v; return this; }
        public Builder summary(String v) { r.summary = v; return this; }
        public Builder probableRootCause(String v) { r.probableRootCause = v; return this; }
        public Builder evidence(List<String> v) { r.evidence = v; return this; }
        public Builder grafanaExploreUrl(String v) { r.grafanaExploreUrl = v; return this; }
        public Builder grafanaDashboardUrl(String v) { r.grafanaDashboardUrl = v; return this; }
        public InvestigationResponse build() { return r; }
    }
}
