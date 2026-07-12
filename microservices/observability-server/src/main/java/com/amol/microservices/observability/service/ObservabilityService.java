package com.amol.microservices.observability.service;

import com.amol.microservices.observability.client.LokiClient;
import com.amol.microservices.observability.client.PrometheusClient;
import com.amol.microservices.observability.dto.LatencyPercentilesResponseDto;
import com.amol.microservices.observability.dto.LogsResponseDto;
import com.amol.microservices.observability.dto.MetricsResponseDto;
import com.amol.microservices.observability.dto.PercentileSeriesDto;
import com.amol.microservices.observability.dto.ServicesResponseDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObservabilityService {

    /** Percentiles reported by {@link #getLatencyPercentiles}, from median to tail. */
    private static final double[] LATENCY_QUANTILES = {0.50, 0.90, 0.95, 0.99};

    private final LokiClient lokiClient;
    private final PrometheusClient prometheusClient;

    public ObservabilityService(LokiClient lokiClient, PrometheusClient prometheusClient) {
        this.lokiClient = lokiClient;
        this.prometheusClient = prometheusClient;
    }

    public LogsResponseDto getLogsByRequestId(String requestId, Instant start, Instant end) {
        return lokiClient.queryByRequestId(requestId, start, end);
    }

    public LogsResponseDto getLogsByService(String serviceName, Instant start, Instant end) {
        return lokiClient.queryByService(serviceName, start, end);
    }

    public LogsResponseDto getErrorLogsByService(String serviceName, Instant start, Instant end) {
        return lokiClient.queryErrorByService(serviceName, start, end);
    }

    public MetricsResponseDto getHeapMetrics(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        return prometheusClient.queryRange("sum(jvm_memory_used_bytes)", serviceName, start, end, stepSeconds);
    }

    public MetricsResponseDto getHeapMaxMetrics(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        return prometheusClient.queryRange("sum(jvm_memory_max_bytes)", serviceName, start, end, stepSeconds);
    }

    public MetricsResponseDto getThreadMetrics(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        return prometheusClient.queryRange("jvm_threads_live_threads", serviceName, start, end, stepSeconds);
    }

    public MetricsResponseDto getRequestRateMetrics(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        return prometheusClient.queryRange(
                "sum(rate(http_server_requests_seconds_count[1m]))",
                serviceName,
                start,
                end,
                stepSeconds);
    }

    public LatencyPercentilesResponseDto getLatencyPercentiles(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        List<PercentileSeriesDto> series = new ArrayList<>();
        for (double quantile : LATENCY_QUANTILES) {
            MetricsResponseDto response = prometheusClient.queryLatencyPercentile(quantile, serviceName, start, end, stepSeconds);
            series.add(new PercentileSeriesDto(response.metric(), quantile, response.points()));
        }
        return new LatencyPercentilesResponseDto(serviceName, series);
    }

    public ServicesResponseDto listObservableServices() {
        return new ServicesResponseDto(List.of("product-service", "images-service", "ecommerce-service"));
    }
}
