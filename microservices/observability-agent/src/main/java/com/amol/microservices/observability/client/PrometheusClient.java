package com.amol.microservices.observability.client;

import com.amol.microservices.observability.config.ObservabilityProperties;
import com.amol.microservices.observability.dto.MetricPointDto;
import com.amol.microservices.observability.dto.MetricsResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class PrometheusClient {

    private final HttpClient httpClient;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;

    public PrometheusClient(ObservabilityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public MetricsResponseDto queryRange(String metricName, String serviceName, Instant start, Instant end, Integer stepSeconds) {
        if (properties.getPrometheus().getBaseUrl() == null || properties.getPrometheus().getBaseUrl().isBlank()) {
            return new MetricsResponseDto(serviceName, metricName, List.of());
        }
        int step = stepSeconds != null ? stepSeconds : defaultStepSeconds(start, end);
        String query = buildQuery(metricName, serviceName);
        Instant rangeStart = start != null ? start : Instant.now().minusSeconds(300);
        Instant rangeEnd = end != null ? end : Instant.now();
        try {
            return new MetricsResponseDto(serviceName, metricName,
                    parseMetricPoints(fetch(buildQueryRangeUrl(query, rangeStart, rangeEnd, step))));
        } catch (Exception ex) {
            throw new RuntimeException("Prometheus query failed: " + ex.getMessage(), ex);
        }
    }

    private String fetch(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .timeout(Duration.ofSeconds(properties.getPrometheus().getTimeoutSeconds()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private URI buildQueryRangeUrl(String query, Instant start, Instant end, int step) {
        String base = requirePrometheusBaseUrl().replaceAll("/+$", "");
        String encodedQuery = UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
        return URI.create(base + "/api/v1/query_range?query=" + encodedQuery
                + "&start=" + start.getEpochSecond()
                + "&end=" + end.getEpochSecond()
                + "&step=" + step);
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

    String buildQuery(String metricName, String serviceName) {
        String jobName = toJobName(serviceName);
        if (metricName.contains("{")) {
            return metricName;
        }
        if (metricName.startsWith("sum(rate(") && metricName.endsWith("))")) {
            String rateExpr = metricName.substring(4, metricName.length() - 1);
            return "sum(" + injectJobIntoRate(rateExpr, jobName) + ")";
        }
        if (metricName.startsWith("sum(") && metricName.endsWith(")")) {
            String inner = metricName.substring(4, metricName.length() - 1).trim();
            return "sum(" + labeledGaugeMetric(inner, jobName) + ")";
        }
        if (metricName.startsWith("rate(")) {
            return injectJobIntoRate(metricName, jobName);
        }
        return labeledGaugeMetric(metricName, jobName);
    }

    private String labeledGaugeMetric(String metricName, String jobName) {
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

    private String requirePrometheusBaseUrl() {
        String baseUrl = properties.getPrometheus().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "observability.prometheus.base-url is not configured (set OBSERVABILITY_PROMETHEUS_BASE_URL)");
        }
        return baseUrl.trim();
    }
}
