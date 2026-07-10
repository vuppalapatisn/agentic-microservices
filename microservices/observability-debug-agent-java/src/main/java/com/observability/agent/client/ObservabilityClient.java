package com.observability.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.observability.agent.config.AgentProperties;
import com.observability.agent.model.LogFinding;
import com.observability.agent.model.MetricFinding;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the observability-server MCP backend.
 * Mirrors Python ObservabilityAgentClient.
 */
@Component
public class ObservabilityClient {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityClient.class);

    private final WebClient client;
    private final AgentProperties props;

    public ObservabilityClient(@Qualifier("observabilityWebClient") WebClient client,
                               AgentProperties props) {
        this.client = client;
        this.props = props;
    }

    @PostConstruct
    public void validateDependencies() {
        int retries = props.getStartupValidationRetries();
        long delayMs = props.getStartupValidationRetrySeconds() * 1000;

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                client.get()
                        .uri("/api/observability/services")
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                        .block();
                log.info("{\"message\":\"startup_validation_complete\",\"service\":\"observability-debug-agent\"}");
                return;
            } catch (Exception e) {
                log.warn("{\"message\":\"startup_validation_retry\",\"attempt\":{},\"error\":\"{}\"}", attempt, e.getMessage());
                if (attempt < retries) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw new IllegalStateException("observability-server is unreachable after " + retries + " retries");
    }

    public List<LogFinding> getLogsByCorrelationId(String correlationId, String startTime, String endTime) {
        return fetchLogs("/api/observability/logs/request/" + correlationId
                + "?start=" + startTime + "&end=" + endTime);
    }

    public List<LogFinding> getLogsByService(String service, String startTime, String endTime) {
        return fetchLogs("/api/observability/logs/service/" + service
                + "?start=" + startTime + "&end=" + endTime);
    }

    public List<LogFinding> getErrorLogsByService(String service, String startTime, String endTime) {
        return fetchLogs("/api/observability/logs/errors/" + service
                + "?start=" + startTime + "&end=" + endTime);
    }

    public List<MetricFinding> getHeapMetrics(String service, String startTime, String endTime) {
        return fetchMetrics("/api/observability/metrics/heap/" + service
                + "?start=" + startTime + "&end=" + endTime);
    }

    public List<MetricFinding> getHeapMaxMetrics(String service, String startTime, String endTime) {
        return fetchMetrics("/api/observability/metrics/heap-max/" + service
                + "?start=" + startTime + "&end=" + endTime);
    }

    public List<MetricFinding> getThreadMetrics(String service, String startTime, String endTime) {
        return fetchMetrics("/api/observability/metrics/threads/" + service
                + "?start=" + startTime + "&end=" + endTime);
    }

    public List<MetricFinding> getRequestRate(String service, String startTime, String endTime) {
        return fetchMetrics("/api/observability/metrics/request-rate/" + service
                + "?start=" + startTime + "&end=" + endTime);
    }

    private List<LogFinding> fetchLogs(String uri) {
        long start = System.currentTimeMillis();
        try {
            JsonNode root = client.get().uri(uri).retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();

            List<LogFinding> result = new ArrayList<>();
            if (root != null && root.has("logs")) {
                for (JsonNode n : root.get("logs")) {
                    result.add(new LogFinding(
                            n.path("timestamp").asText(),
                            n.path("service").asText(),
                            n.path("level").asText(),
                            n.path("message").asText()
                    ));
                }
            }
            return result;
        } catch (WebClientResponseException e) {
            log.warn("{\"message\":\"observability_client_error\",\"uri\":\"{}\",\"status\":{}}", uri, e.getStatusCode().value());
            return List.of();
        } catch (Exception e) {
            log.warn("{\"message\":\"observability_client_error\",\"uri\":\"{}\",\"error\":\"{}\"}", uri, e.getMessage());
            return List.of();
        } finally {
            log.debug("{\"message\":\"observability_client_call\",\"uri\":\"{}\",\"durationMs\":{}}", uri, System.currentTimeMillis() - start);
        }
    }

    private List<MetricFinding> fetchMetrics(String uri) {
        long start = System.currentTimeMillis();
        try {
            JsonNode root = client.get().uri(uri).retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(props.getRequestTimeoutSeconds()))
                    .block();

            List<MetricFinding> result = new ArrayList<>();
            if (root != null && root.has("points")) {
                for (JsonNode n : root.get("points")) {
                    result.add(new MetricFinding(
                            n.path("timestamp").asText(),
                            n.path("value").asDouble()
                    ));
                }
            }
            return result;
        } catch (WebClientResponseException e) {
            log.warn("{\"message\":\"observability_client_error\",\"uri\":\"{}\",\"status\":{}}", uri, e.getStatusCode().value());
            return List.of();
        } catch (Exception e) {
            log.warn("{\"message\":\"observability_client_error\",\"uri\":\"{}\",\"error\":\"{}\"}", uri, e.getMessage());
            return List.of();
        } finally {
            log.debug("{\"message\":\"observability_client_call\",\"uri\":\"{}\",\"durationMs\":{}}", uri, System.currentTimeMillis() - start);
        }
    }
}
