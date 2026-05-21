package com.amol.microservices.observability.client;

import com.amol.microservices.observability.config.ObservabilityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrometheusClientTest {

    private final PrometheusClient client = new PrometheusClient(new ObservabilityProperties(), new ObjectMapper());

    @Test
    void buildQuery_sumsRequestRateAcrossAllHttpSeries() {
        String query = client.buildQuery(
                "sum(rate(http_server_requests_seconds_count[1m]))",
                "ecommerce-service");
        assertEquals(
                "sum(rate(http_server_requests_seconds_count{job=\"ecommerce\"}[1m]))",
                query);
    }

    @Test
    void buildQuery_injectsJobIntoSimpleRate() {
        String query = client.buildQuery(
                "rate(http_server_requests_seconds_count[1m])",
                "ecommerce-service");
        assertEquals(
                "rate(http_server_requests_seconds_count{job=\"ecommerce\"}[1m])",
                query);
    }

    @Test
    void buildQuery_sumsHeapUsedAcrossAllPools() {
        String query = client.buildQuery("sum(jvm_memory_used_bytes)", "ecommerce-service");
        assertEquals("sum(jvm_memory_used_bytes{job=\"ecommerce\",area=\"heap\"})", query);
    }

    @Test
    void buildQuery_addsHeapLabelForUnsummedMemoryMetrics() {
        String query = client.buildQuery("jvm_memory_used_bytes", "ecommerce-service");
        assertEquals("jvm_memory_used_bytes{job=\"ecommerce\",area=\"heap\"}", query);
    }
}
