package com.amol.microservices.observability.client;

import com.amol.microservices.observability.config.ObservabilityProperties;
import com.amol.microservices.observability.dto.LogEntryDto;
import com.amol.microservices.observability.dto.LogsResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class LokiClient {

    private final WebClient webClient;
    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper;

    public LokiClient(WebClient.Builder builder, ObservabilityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String base = properties.getLoki().getBaseUrl();
        this.webClient = builder
                .baseUrl(base == null ? "" : base)
                .build();
    }

    public LogsResponseDto queryByRequestId(String requestId, Instant start, Instant end) {
        if (properties.getLoki().getBaseUrl() == null || properties.getLoki().getBaseUrl().isBlank()) {
            return new LogsResponseDto(requestId, List.of());
        }

        try {
            String query = "{namespace=~\"ecommerce|observability\"} |= \"" + escapeLogql(requestId) + "\"";
            List<LogEntryDto> logs = executeQuery(query, start, end).stream()
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                    .toList();
            return new LogsResponseDto(requestId, logs);
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("Loki query failed", ex);
        }
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
        String response = null;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/loki/api/v1/query_range")
                                .queryParam("query", query)
                                .queryParam("limit", 1000)
                                .queryParam("direction", "forward");
                        if (start != null) {
                            builder.queryParam("start", toNanos(start));
                        }
                        if (end != null) {
                            builder.queryParam("end", toNanos(end));
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, c -> Mono.error(new WebClientResponseException("Loki error", 500, "", null, null, null)))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.getLoki().getTimeoutSeconds()))
                    .block();
            return parseLogs(response);
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("Loki query failed", ex);
        } catch (Exception ex) {
            String snippet = response == null ? "null" : response.substring(0, Math.min(response.length(), 300));
            throw new RuntimeException("Failed to parse Loki response: " + ex.getMessage() + " snippet=" + snippet, ex);
        }
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
        // Loki query_range uses nanoseconds; some payloads use seconds.
        if (value < 1_000_000_000_000L) {
            return value * 1_000_000_000L;
        }
        return value;
    }

    private String textValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? defaultValue : field.asText(defaultValue);
    }

    private String toNanos(Instant instant) {
        return String.valueOf((instant.getEpochSecond() * 1_000_000_000L) + instant.getNano());
    }
}
