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
    void buildQuery_sumsHeapMaxAcrossAllPools() {
        String query = client.buildQuery("sum(jvm_memory_max_bytes)", "ecommerce-service");
        assertEquals("sum(jvm_memory_max_bytes{job=\"ecommerce\",area=\"heap\"})", query);
    }

    @Test
    void buildQuery_addsHeapLabelForUnsummedMemoryMetrics() {
        String query = client.buildQuery("jvm_memory_used_bytes", "ecommerce-service");
        assertEquals("jvm_memory_used_bytes{job=\"ecommerce\",area=\"heap\"}", query);
    }

    @Test
    void buildLatencyPercentileQuery_wrapsBucketsInHistogramQuantile() {
        String query = client.buildLatencyPercentileQuery(0.99, "product-service");
        assertEquals(
                "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job=\"product\"}[1m])) by (le))",
                query);
    }

    @Test
    void buildLatencyPercentileQuery_stripsServiceSuffixForJobLabel() {
        String query = client.buildLatencyPercentileQuery(0.9, "ecommerce-service");
        assertEquals(
                "histogram_quantile(0.9, sum(rate(http_server_requests_seconds_bucket{job=\"ecommerce\"}[1m])) by (le))",
                query);
    }

    @Test
    void queryLatencyPercentile_returnsEmptyWhenBaseUrlNotConfigured() {
        // Default ObservabilityProperties has no Prometheus base-url, so no network call is made.
        var response = client.queryLatencyPercentile(0.95, "product-service", null, null, null);
        assertEquals("product-service", response.service());
        assertEquals("http_latency_p95", response.metric());
        assertEquals(0, response.points().size());
    }
}
