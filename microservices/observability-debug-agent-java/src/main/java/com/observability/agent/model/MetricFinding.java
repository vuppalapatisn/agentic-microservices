package com.observability.agent.model;

public class MetricFinding {

    private String timestamp;
    private double value;

    public MetricFinding() {}

    public MetricFinding(String timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String v) { this.timestamp = v; }

    public double getValue() { return value; }
    public void setValue(double v) { this.value = v; }
}
