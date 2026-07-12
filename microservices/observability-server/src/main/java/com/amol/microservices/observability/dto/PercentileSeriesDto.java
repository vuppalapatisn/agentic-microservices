package com.amol.microservices.observability.dto;

import java.util.List;

/**
 * One latency percentile series over time (e.g. label "http_latency_p99", quantile 0.99).
 */
public record PercentileSeriesDto(String label, double quantile, List<MetricPointDto> points) {
}
