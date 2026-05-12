package com.amol.microservices.observability.service;

import com.amol.microservices.observability.client.LokiClient;
import com.amol.microservices.observability.client.PrometheusClient;
import com.amol.microservices.observability.dto.LogEntryDto;
import com.amol.microservices.observability.dto.LogsResponseDto;
import com.amol.microservices.observability.dto.MetricsResponseDto;
import com.amol.microservices.observability.dto.MetricPointDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ObservabilityService {

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
        // reuse loki client but filter for error levels in real implementation
        return lokiClient.queryByService(serviceName, start, end);
    }

    public MetricsResponseDto getHeapMetrics(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        return prometheusClient.queryRange("jvm_memory_used_bytes", serviceName, start, end, stepSeconds);
    }

    public MetricsResponseDto getThreadMetrics(String serviceName, Instant start, Instant end, Integer stepSeconds) {
        return prometheusClient.queryRange("jvm_threads_live_threads", serviceName, start, end, stepSeconds);
    }
}
