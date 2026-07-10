package com.observability.agent.correlation;

import com.observability.agent.model.CorrelationFinding;
import com.observability.agent.model.InvestigationContext;
import com.observability.agent.model.LogFinding;
import com.observability.agent.model.MetricFinding;
import com.observability.agent.util.Formatting;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based correlation engine.
 * Scores five root-cause categories using heuristics applied to the
 * collected metrics and logs. Mirrors Python CorrelationEngine.
 */
@Component
public class CorrelationEngine {

    private static final double HEAP_SPIKE_RATIO    = 1.5;
    private static final double THREAD_SPIKE_RATIO  = 1.5;
    private static final double THREAD_SPIKE_DELTA  = 20.0;
    private static final double RPS_SPIKE_RATIO     = 1.8;
    private static final long   SLOW_REQUEST_THRESHOLD_MS = 5000;

    public CorrelationFinding correlate(InvestigationContext ctx) {
        if (ctx.isHeapUsagePercentQuery()) {
            return correlateHeapPercent(ctx);
        }
        return correlateFullAnalysis(ctx);
    }

    private CorrelationFinding correlateHeapPercent(InvestigationContext ctx) {
        List<MetricFinding> heap    = ctx.getHeapMetrics();
        List<MetricFinding> heapMax = ctx.getHeapMaxMetrics();

        if (heap.isEmpty() || heapMax.isEmpty()) {
            return new CorrelationFinding("insufficient telemetry data",
                    List.of("No heap metrics available"));
        }

        double used    = latest(heap);
        double max     = latest(heapMax);
        double percent = max > 0 ? (used / max) * 100.0 : 0.0;

        String evidence = String.format("Heap usage: %s used of %s max (%.1f%%)",
                Formatting.formatBytes(used), Formatting.formatBytes(max), percent);
        return new CorrelationFinding("resource saturation", List.of(evidence));
    }

    private CorrelationFinding correlateFullAnalysis(InvestigationContext ctx) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("resource saturation",        0);
        scores.put("traffic overload",           0);
        scores.put("downstream dependency issue",0);
        scores.put("request-specific failure",   0);
        scores.put("insufficient telemetry data",0);

        List<String> evidence = new ArrayList<>();
        Set<String> tags = new HashSet<>();

        // --- request rate spike ---
        List<MetricFinding> rps = ctx.getRequestRateMetrics();
        if (rps.size() >= 2) {
            double peakRps = peak(rps);
            double avgRps  = average(rps);
            if (avgRps > 0 && peakRps > avgRps * RPS_SPIKE_RATIO) {
                scores.merge("traffic overload", 2, Integer::sum);
                evidence.add(String.format("Traffic spike: peak %s vs average %s",
                        Formatting.formatRps(peakRps), Formatting.formatRps(avgRps)));
            }
        }

        // --- heap spike ---
        List<MetricFinding> heap = ctx.getHeapMetrics();
        if (heap.size() >= 2) {
            double peakHeap = peak(heap);
            double avgHeap  = average(heap);
            if (peakHeap > 0) {
                evidence.add(String.format("Heap averaged %s, peaked at %s",
                        Formatting.formatBytes(avgHeap), Formatting.formatBytes(peakHeap)));
            }
            if (avgHeap > 0 && peakHeap > avgHeap * HEAP_SPIKE_RATIO) {
                tags.add("heap-spike");
                scores.merge("resource saturation", 2, Integer::sum);
                evidence.add("Heap spike detected: peak exceeded 1.5x average");
            }
        }

        // --- thread spike ---
        List<MetricFinding> threads = ctx.getThreadMetrics();
        if (threads.size() >= 2) {
            double peakThreads = peak(threads);
            double avgThreads  = average(threads);
            evidence.add(String.format("Thread count averaged %s, peaked at %s",
                    Formatting.formatCount(avgThreads), Formatting.formatCount(peakThreads)));
            if (peakThreads > avgThreads * THREAD_SPIKE_RATIO
                    || peakThreads > avgThreads + THREAD_SPIKE_DELTA) {
                tags.add("thread-spike");
                scores.merge("resource saturation", 2, Integer::sum);
            }
        }

        // --- combined JVM pressure ---
        boolean hasSlowLogs = ctx.getLogs().stream()
                .anyMatch(l -> { Long d = l.getDurationMs(); return d != null && d > SLOW_REQUEST_THRESHOLD_MS; });
        if (tags.contains("heap-spike") && tags.contains("thread-spike") && hasSlowLogs) {
            scores.merge("resource saturation", 2, Integer::sum);
            evidence.add("Combined JVM pressure: heap spike + thread spike + slow requests");
        }

        // --- cross-service correlation ---
        List<LogFinding> allLogs = new ArrayList<>(ctx.getLogs());
        allLogs.addAll(ctx.getErrorLogs());
        if (ctx.getRequestId() != null && !ctx.getRequestId().isEmpty()) {
            Set<String> servicesWithId = allLogs.stream()
                    .filter(l -> l.getMessage() != null && l.getMessage().contains(ctx.getRequestId()))
                    .map(LogFinding::getService)
                    .collect(Collectors.toSet());
            if (servicesWithId.size() > 1) {
                scores.merge("request-specific failure", 2, Integer::sum);
                evidence.add("Request ID found in multiple services: " + servicesWithId);
                boolean hasErrors = allLogs.stream()
                        .anyMatch(l -> l.getLevel() != null && l.getLevel().equalsIgnoreCase("ERROR"));
                if (hasErrors) {
                    scores.merge("downstream dependency issue", 2, Integer::sum);
                    evidence.add("Errors correlated across services for this request");
                }
            }
        }

        // error log summary
        if (!ctx.getErrorLogs().isEmpty()) {
            evidence.add(String.format("Found %d error log entries", ctx.getErrorLogs().size()));
        }

        // telemetry completeness check
        boolean noData = heap.isEmpty() && threads.isEmpty() && rps.isEmpty()
                && ctx.getLogs().isEmpty() && ctx.getErrorLogs().isEmpty();
        if (noData) {
            scores.merge("insufficient telemetry data", 5, Integer::sum);
            evidence.add("No metrics or logs returned from observability-server");
        }

        String probableRootCause = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("insufficient telemetry data");

        return new CorrelationFinding(probableRootCause, evidence);
    }

    private double peak(List<MetricFinding> points) {
        return points.stream().mapToDouble(MetricFinding::getValue).max().orElse(0);
    }

    private double latest(List<MetricFinding> points) {
        return points.isEmpty() ? 0 : points.get(points.size() - 1).getValue();
    }

    private double average(List<MetricFinding> points) {
        return points.stream().mapToDouble(MetricFinding::getValue).average().orElse(0);
    }
}
