package com.amol.microservices.observability.client;

import com.amol.microservices.observability.config.ObservabilityProperties;
import com.amol.microservices.observability.dto.MetricPointDto;
import com.amol.microservices.observability.dto.MetricsResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class PrometheusClient {

    private final WebClient webClient;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;

    public PrometheusClient(WebClient.Builder builder, ObservabilityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String base = properties.getPrometheus().getBaseUrl();
        this.webClient = builder.baseUrl(base == null ? "" : base).build();
    }

    public MetricsResponseDto queryRange(String metricName, String serviceName, Instant start, Instant end, Integer stepSeconds) {
        if (properties.getPrometheus().getBaseUrl() == null || properties.getPrometheus().getBaseUrl().isBlank()) {
            return new MetricsResponseDto(serviceName, metricName, List.of());
        }
        int step = stepSeconds != null ? stepSeconds : defaultStepSeconds(start, end);
        String query = buildQuery(metricName, serviceName);
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/query_range")
                            .queryParam("query", query)
                            .queryParam("start", start != null ? start.toString() : Instant.now().minusSeconds(300).toString())
                            .queryParam("end", end != null ? end.toString() : Instant.now().toString())
                            .queryParam("step", step)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.getPrometheus().getTimeoutSeconds()))
                    .block();
            return new MetricsResponseDto(serviceName, metricName, parseMetricPoints(response));
        } catch (Exception ex) {
            throw new RuntimeException("Prometheus query failed", ex);
        }
    }

    private List<MetricPointDto> parseMetricPoints(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode results = root.path("data").path("result");
        if (!results.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(results.spliterator(), false)
                .flatMap(result -> {
                    JsonNode values = result.path("values");
                    if (!values.isArray()) {
                        return java.util.stream.Stream.<MetricPointDto>empty();
                    }
                    return StreamSupport.stream(values.spliterator(), false)
                            .map(this::mapMetricPoint)
                            .filter(point -> point != null);
                })
                .toList();
    }

    private MetricPointDto mapMetricPoint(JsonNode node) {
        if (!node.isArray() || node.size() < 2) {
            return null;
        }
        long epochSeconds = node.get(0).asLong();
        double value = Double.parseDouble(node.get(1).asText());
        return new MetricPointDto(Instant.ofEpochSecond(epochSeconds), value);
    }

    private int defaultStepSeconds(Instant start, Instant end) {
        if (start == null || end == null) {
            return 15;
        }
        long seconds = Math.max(1, Duration.between(start, end).getSeconds());
        return (int) Math.max(1, seconds / 200);
    }

    private String toJobName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return "";
        }
        String s = serviceName.trim();
        if (s.endsWith("-service")) {
            return s.substring(0, s.length() - "-service".length());
        }
        return s;
    }

    private String buildQuery(String metricName, String serviceName) {
        String jobName = toJobName(serviceName);
        if (metricName.contains("{")) {
            return metricName;
        }
        if (metricName.startsWith("rate(")) {
            return injectJobIntoRate(metricName, jobName);
        }
        StringBuilder labels = new StringBuilder("job=\"").append(jobName).append("\"");
        if ("jvm_memory_used_bytes".equals(metricName) || "jvm_memory_max_bytes".equals(metricName)) {
            labels.append(",area=\"heap\"");
        }
        return metricName + "{" + labels + "}";
    }

    private String injectJobIntoRate(String metricName, String jobName) {
        int open = metricName.indexOf('(');
        int close = metricName.lastIndexOf('[');
        if (open < 0 || close < 0 || close <= open) {
            return metricName;
        }
        String inner = metricName.substring(open + 1, close).trim();
        String windowAndRest = metricName.substring(close);
        String labeledInner;
        if (inner.contains("{")) {
            labeledInner = inner;
        } else {
            labeledInner = inner + "{job=\"" + jobName + "\"}";
        }
        return "rate(" + labeledInner + windowAndRest;
    }
}
