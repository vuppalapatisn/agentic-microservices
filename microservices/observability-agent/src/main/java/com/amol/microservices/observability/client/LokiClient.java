package com.amol.microservices.observability.client;

import com.amol.microservices.observability.config.ObservabilityProperties;
import com.amol.microservices.observability.dto.LogEntryDto;
import com.amol.microservices.observability.dto.LogsResponseDto;
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
public class LokiClient {

    private final HttpClient httpClient;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;

    public LokiClient(ObservabilityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public LogsResponseDto queryByRequestId(String requestId, Instant start, Instant end) {
        if (properties.getLoki().getBaseUrl() == null || properties.getLoki().getBaseUrl().isBlank()) {
            return new LogsResponseDto(requestId, List.of());
        }
        String id = requestId == null ? "" : requestId.trim();
        String query = "{namespace=~\"ecommerce|observability\"} |= \"" + escapeLogql(id) + "\"";
        List<LogEntryDto> logs = executeQuery(query, start, end);
        return new LogsResponseDto(id, logs);
    }

    public LogsResponseDto queryByService(String serviceName, Instant start, Instant end) {
        if (properties.getLoki().getBaseUrl() == null || properties.getLoki().getBaseUrl().isBlank()) {
            return new LogsResponseDto(null, List.of());
        }
        String query = logStreamSelector(serviceName);
        return new LogsResponseDto(null, executeQuery(query, start, end));
    }

    public LogsResponseDto queryErrorByService(String serviceName, Instant start, Instant end) {
        if (properties.getLoki().getBaseUrl() == null || properties.getLoki().getBaseUrl().isBlank()) {
            return new LogsResponseDto(null, List.of());
        }
        String query = logStreamSelector(serviceName) + " |~ \"(?i).*(ERROR|WARN).*\"";
        return new LogsResponseDto(null, executeQuery(query, start, end));
    }

    static String normalizeLogServiceField(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return "";
        }
        String s = serviceName.trim();
        return switch (s) {
            case "ecommerce-service" -> "ecommerce";
            case "product-service" -> "product";
            case "images-service" -> "images";
            default -> s;
        };
    }

    private String logStreamSelector(String serviceName) {
        String app = normalizeLogServiceField(serviceName);
        if ("observability-agent".equals(app)) {
            return "{namespace=\"observability\",app=\"observability-agent\"}";
        }
        return "{namespace=\"ecommerce\",app=\"" + escapeLogql(app) + "\"}";
    }

    private static String escapeLogql(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<LogEntryDto> executeQuery(String query, Instant start, Instant end) {
        Instant rangeStart = resolveStart(start);
        Instant rangeEnd = resolveEnd(end);
        try {
            return parseLogs(fetch(buildQueryRangeUrl(query, rangeStart, rangeEnd)));
        } catch (Exception ex) {
            throw new RuntimeException("Loki query failed: " + ex.getMessage(), ex);
        }
    }

    private String fetch(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .timeout(Duration.ofSeconds(properties.getLoki().getTimeoutSeconds()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private URI buildQueryRangeUrl(String query, Instant start, Instant end) {
        String base = requireLokiBaseUrl().replaceAll("/+$", "");
        String encodedQuery = UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
        return URI.create(base + "/loki/api/v1/query_range?query=" + encodedQuery
                + "&limit=1000&direction=forward&start=" + toNanos(start) + "&end=" + toNanos(end));
    }

    private List<LogEntryDto> parseLogs(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        if (!"success".equalsIgnoreCase(root.path("status").asText())) {
            String error = root.path("error").asText(root.toString());
            throw new IllegalStateException("Loki query unsuccessful: " + error);
        }
        JsonNode results = root.path("data").path("result");
        if (!results.isArray()) {
            return List.of();
        }

        return StreamSupport.stream(results.spliterator(), false)
                .flatMap(result -> {
                    JsonNode stream = result.path("stream");
                    String service = textValue(stream, "app", textValue(stream, "service", "unknown"));
                    JsonNode values = result.path("values");
                    if (!values.isArray()) {
                        return java.util.stream.Stream.<LogEntryDto>empty();
                    }
                    return StreamSupport.stream(values.spliterator(), false)
                            .map(node -> mapLogEntry(node, service))
                            .filter(entry -> entry != null);
                })
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
    }

    private LogEntryDto mapLogEntry(JsonNode valueNode, String fallbackService) {
        if (!valueNode.isArray() || valueNode.size() < 2) {
            return null;
        }
        try {
            Instant timestamp = parseLokiTimestamp(valueNode.get(0));
            String rawLog = valueNode.get(1).asText();
            try {
                JsonNode logNode = objectMapper.readTree(rawLog);
                String service = textValue(logNode, "service", fallbackService);
                String level = textValue(logNode, "level", "INFO");
                String message = textValue(logNode, "message", rawLog);
                return new LogEntryDto(timestamp, service, level, message);
            } catch (Exception ex) {
                return new LogEntryDto(timestamp, fallbackService, "INFO", rawLog);
            }
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static Instant parseLokiTimestamp(JsonNode timestampNode) {
        if (timestampNode == null || timestampNode.isNull() || timestampNode.isMissingNode()) {
            throw new IllegalArgumentException("Missing Loki timestamp");
        }

        long epochNanos = toEpochNanos(timestampNode);
        long seconds = Math.floorDiv(epochNanos, 1_000_000_000L);
        int nanos = (int) Math.floorMod(epochNanos, 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static long toEpochNanos(JsonNode timestampNode) {
        if (timestampNode.isIntegralNumber()) {
            return normalizeToEpochNanos(timestampNode.asLong());
        }
        if (timestampNode.isFloatingPointNumber()) {
            return normalizeToEpochNanos((long) timestampNode.asDouble());
        }

        String text = timestampNode.asText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Blank Loki timestamp");
        }
        try {
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                return normalizeToEpochNanos((long) Double.parseDouble(text));
            }
            return normalizeToEpochNanos(Long.parseLong(text));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed Loki timestamp: " + text, ex);
        }
    }

    private static long normalizeToEpochNanos(long value) {
        if (value < 1_000_000_000_000L) {
            return value * 1_000_000_000L;
        }
        return value;
    }

    private String textValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? defaultValue : field.asText(defaultValue);
    }

    private Instant resolveStart(Instant start) {
        return start != null ? start : Instant.now().minusSeconds(1800);
    }

    private Instant resolveEnd(Instant end) {
        return end != null ? end : Instant.now();
    }

    private String requireLokiBaseUrl() {
        String baseUrl = properties.getLoki().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "observability.loki.base-url is not configured (set OBSERVABILITY_LOKI_BASE_URL)");
        }
        return baseUrl.trim();
    }

    private static String toNanos(Instant instant) {
        return String.valueOf((instant.getEpochSecond() * 1_000_000_000L) + instant.getNano());
    }
}
