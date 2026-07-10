package com.observability.agent.model;

import java.util.ArrayList;
import java.util.List;

public class InvestigationContext {

    private String serviceName;
    private String requestId;
    private String startTime;
    private String endTime;
    private String query;
    private List<LogFinding> logs = new ArrayList<>();
    private List<LogFinding> errorLogs = new ArrayList<>();
    private List<MetricFinding> heapMetrics = new ArrayList<>();
    private List<MetricFinding> heapMaxMetrics = new ArrayList<>();
    private List<MetricFinding> threadMetrics = new ArrayList<>();
    private List<MetricFinding> requestRateMetrics = new ArrayList<>();
    private boolean heapUsagePercentQuery = false;

    // fetch flags set by classification
    private boolean fetchLogs;
    private boolean fetchErrorLogs;
    private boolean fetchHeapMetrics;
    private boolean fetchHeapMaxMetrics;
    private boolean fetchThreadMetrics;
    private boolean fetchRequestRate;

    public String getServiceName() { return serviceName; }
    public void setServiceName(String v) { this.serviceName = v; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String v) { this.startTime = v; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String v) { this.endTime = v; }

    public String getQuery() { return query; }
    public void setQuery(String v) { this.query = v; }

    public List<LogFinding> getLogs() { return logs; }
    public void setLogs(List<LogFinding> v) { this.logs = v; }

    public List<LogFinding> getErrorLogs() { return errorLogs; }
    public void setErrorLogs(List<LogFinding> v) { this.errorLogs = v; }

    public List<MetricFinding> getHeapMetrics() { return heapMetrics; }
    public void setHeapMetrics(List<MetricFinding> v) { this.heapMetrics = v; }

    public List<MetricFinding> getHeapMaxMetrics() { return heapMaxMetrics; }
    public void setHeapMaxMetrics(List<MetricFinding> v) { this.heapMaxMetrics = v; }

    public List<MetricFinding> getThreadMetrics() { return threadMetrics; }
    public void setThreadMetrics(List<MetricFinding> v) { this.threadMetrics = v; }

    public List<MetricFinding> getRequestRateMetrics() { return requestRateMetrics; }
    public void setRequestRateMetrics(List<MetricFinding> v) { this.requestRateMetrics = v; }

    public boolean isHeapUsagePercentQuery() { return heapUsagePercentQuery; }
    public void setHeapUsagePercentQuery(boolean v) { this.heapUsagePercentQuery = v; }

    public boolean isFetchLogs() { return fetchLogs; }
    public void setFetchLogs(boolean v) { this.fetchLogs = v; }

    public boolean isFetchErrorLogs() { return fetchErrorLogs; }
    public void setFetchErrorLogs(boolean v) { this.fetchErrorLogs = v; }

    public boolean isFetchHeapMetrics() { return fetchHeapMetrics; }
    public void setFetchHeapMetrics(boolean v) { this.fetchHeapMetrics = v; }

    public boolean isFetchHeapMaxMetrics() { return fetchHeapMaxMetrics; }
    public void setFetchHeapMaxMetrics(boolean v) { this.fetchHeapMaxMetrics = v; }

    public boolean isFetchThreadMetrics() { return fetchThreadMetrics; }
    public void setFetchThreadMetrics(boolean v) { this.fetchThreadMetrics = v; }

    public boolean isFetchRequestRate() { return fetchRequestRate; }
    public void setFetchRequestRate(boolean v) { this.fetchRequestRate = v; }
}
