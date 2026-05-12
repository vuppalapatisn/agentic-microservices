package com.amol.microservices.observability.client;

import com.amol.microservices.observability.dto.LogEntryDto;
import com.amol.microservices.observability.dto.LogsResponseDto;
import com.amol.microservices.observability.config.ObservabilityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class LokiClient {

    private final WebClient webClient;
    private final ObservabilityProperties properties;

    public LokiClient(WebClient.Builder builder, ObservabilityProperties properties) {
        this.properties = properties;
        String base = properties.getLoki().getBaseUrl();
        this.webClient = builder.baseUrl(base == null ? "" : base).build();
    }

    public LogsResponseDto queryByRequestId(String requestId, Instant start, Instant end) {
        // Minimal implementation: query log lines via Loki's simple API if available.
        // For now return an empty response if base-url is not configured.
        if (properties.getLoki().getBaseUrl() == null || properties.getLoki().getBaseUrl().isBlank()) {
            return new LogsResponseDto(requestId, List.of());
        }

        try {
            // Note: real implementation would call Loki HTTP API /loki/api/v1/query_range
            // This is a placeholder demonstrating parsing & mapping.
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/loki/api/v1/query_range").queryParam("query", requestId).build())
                    .retrieve()
                    .onStatus(HttpStatus::isError, c -> Mono.error(new WebClientResponseException("Loki error", 500, "", null, null, null)))
                    .bodyToMono(String.class)
                    .block();

            // Parse would happen here - for now return empty list
            return new LogsResponseDto(requestId, List.of());
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("Loki query failed", ex);
        }
    }

    public LogsResponseDto queryByService(String serviceName, Instant start, Instant end) {
        if (properties.getLoki().getBaseUrl() == null || properties.getLoki().getBaseUrl().isBlank()) {
            return new LogsResponseDto(null, List.of());
        }
        // Placeholder implementation
        return new LogsResponseDto(null, List.of());
    }
}
