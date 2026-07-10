package com.observability.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observability.agent.config.GrafanaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds Grafana Explore (Loki) and Dashboard URLs.
 * Mirrors Python app/util/grafana_links.py.
 */
@Component
public class GrafanaLinks {

    private static final Logger log = LoggerFactory.getLogger(GrafanaLinks.class);

    private final GrafanaProperties grafanaProps;
    private final WebClient grafanaWebClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> uidCache = new ConcurrentHashMap<>();

    public GrafanaLinks(GrafanaProperties grafanaProps,
                        @Qualifier("grafanaWebClient") WebClient grafanaWebClient) {
        this.grafanaProps = grafanaProps;
        this.grafanaWebClient = grafanaWebClient;
    }

    public String buildLokiExploreUrl(String correlationId, String namespace,
                                      String startTime, String endTime) {
        String datasourceUid = resolveLokiDatasourceUid();
        String logql = correlationId != null && !correlationId.isEmpty()
                ? String.format("{correlationId=\"%s\"}", correlationId)
                : String.format("{namespace=\"%s\"}", namespace);

        try {
            long fromMs = toEpochMs(startTime);
            long toMs   = toEpochMs(endTime);

            Map<String, Object> pane = Map.of(
                    "queries", new Object[]{Map.of(
                            "refId", "A",
                            "expr", logql,
                            "datasource", Map.of("type", "loki", "uid", datasourceUid)
                    )},
                    "range", Map.of("from", String.valueOf(fromMs), "to", String.valueOf(toMs))
            );

            String panesJson = mapper.writeValueAsString(Map.of("a", pane));
            String encoded = URLEncoder.encode(panesJson, StandardCharsets.UTF_8);
            return grafanaProps.getBaseUrl() + "/explore?panes=" + encoded + "&schemaVersion=1&orgId=1";
        } catch (Exception e) {
            log.warn("Failed to build Loki explore URL: {}", e.getMessage());
            return null;
        }
    }

    public String buildDashboardUrl(String startTime, String endTime) {
        String dashUid = resolveDashboardUid();
        try {
            long fromMs = toEpochMs(startTime);
            long toMs   = toEpochMs(endTime);
            return grafanaProps.getBaseUrl()
                    + "/d/" + dashUid
                    + "?orgId=1&from=" + fromMs + "&to=" + toMs;
        } catch (Exception e) {
            log.warn("Failed to build dashboard URL: {}", e.getMessage());
            return null;
        }
    }

    private String resolveLokiDatasourceUid() {
        return uidCache.computeIfAbsent("loki", k -> {
            try {
                var node = grafanaWebClient.get()
                        .uri("/api/datasources/name/Loki")
                        .retrieve()
                        .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                        .block();
                return node != null ? node.path("uid").asText(grafanaProps.getLokiDatasourceUid())
                                    : grafanaProps.getLokiDatasourceUid();
            } catch (Exception e) {
                return grafanaProps.getLokiDatasourceUid();
            }
        });
    }

    private String resolveDashboardUid() {
        return uidCache.computeIfAbsent("dashboard", k -> {
            try {
                var nodes = grafanaWebClient.get()
                        .uri("/api/search?type=dash-db")
                        .retrieve()
                        .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                        .block();
                if (nodes != null && nodes.isArray()) {
                    for (var n : nodes) {
                        if ("ecommerce-observability".equalsIgnoreCase(n.path("title").asText())) {
                            return n.path("uid").asText(grafanaProps.getDashboardUid());
                        }
                    }
                }
                return grafanaProps.getDashboardUid();
            } catch (Exception e) {
                return grafanaProps.getDashboardUid();
            }
        });
    }

    private long toEpochMs(String isoTime) {
        try {
            return Instant.parse(isoTime).toEpochMilli();
        } catch (DateTimeParseException e) {
            return Instant.now().toEpochMilli();
        }
    }
}
