package com.observability.agent;

import com.observability.agent.correlation.CorrelationEngine;
import com.observability.agent.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationEngineTest {

    private final CorrelationEngine engine = new CorrelationEngine();

    @Test
    void threadEvidenceUsesWholeNumbersAndAverageLabel() {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setHeapUsagePercentQuery(false);
        ctx.setThreadMetrics(List.of(
                new MetricFinding("2024-01-01T00:00:00Z", 65.12),
                new MetricFinding("2024-01-01T00:01:00Z", 69.43),
                new MetricFinding("2024-01-01T00:02:00Z", 120.00)
        ));

        CorrelationFinding result = engine.correlate(ctx);

        String evidence = String.join(" ", result.getEvidence());
        assertThat(evidence).doesNotContain(".12").doesNotContain(".43");
        assertThat(evidence.toLowerCase()).contains("average");
        assertThat(evidence.toLowerCase()).contains("peak");
    }

    @Test
    void heapPercentQueryReturnsPercentage() {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setHeapUsagePercentQuery(true);
        ctx.setHeapMetrics(List.of(new MetricFinding("t", 536_870_912.0)));   // 512 MB
        ctx.setHeapMaxMetrics(List.of(new MetricFinding("t", 1_073_741_824.0))); // 1 GB

        CorrelationFinding result = engine.correlate(ctx);

        assertThat(result.getProbableRootCause()).isEqualTo("resource saturation");
        assertThat(result.getEvidence().get(0)).contains("50.0%");
    }

    @Test
    void noDataReturnsInsufficientTelemetry() {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setHeapUsagePercentQuery(false);

        CorrelationFinding result = engine.correlate(ctx);

        assertThat(result.getProbableRootCause()).isEqualTo("insufficient telemetry data");
    }
}
