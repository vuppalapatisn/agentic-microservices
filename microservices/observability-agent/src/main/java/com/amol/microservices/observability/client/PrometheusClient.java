package com.amol.microservices.observability.client;

import com.amol.microservices.observability.dto.MetricPointDto;
import com.amol.microservices.observability.dto.MetricsResponseDto;
import com.amol.microservices.observability.config.ObservabilityProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class PrometheusClient {

    private final WebClient webClient;
    private final ObservabilityProperties properties;

    public PrometheusClient(WebClient.Builder builder, ObservabilityProperties properties) {
        this.properties = properties;
        String base = properties.getPrometheus().getBaseUrl();
        this.webClient = builder.baseUrl(base == null ? "" : base).build();
    }

    public MetricsResponseDto queryRange(String metricName, String serviceName, Instant start, Instant end, Integer stepSeconds) {
        if (properties.getPrometheus().getBaseUrl() == null || properties.getPrometheus().getBaseUrl().isBlank()) {
            return new MetricsResponseDto(serviceName, metricName, List.of());
        }
        // Placeholder: real query to /api/v1/query_range
        return new MetricsResponseDto(serviceName, metricName, List.of());
    }
}
