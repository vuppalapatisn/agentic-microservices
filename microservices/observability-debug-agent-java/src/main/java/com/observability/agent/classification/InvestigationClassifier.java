package com.observability.agent.classification;

import com.observability.agent.model.InvestigationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keyword-based query classifier.
 * Mirrors Python classify_investigation() — sets fetch flags on the context
 * based on which keywords appear in the developer's question.
 */
@Component
public class InvestigationClassifier {

    private static final List<String> MONITORING_KEYWORDS = List.of(
            "heap", "memory", "thread", "cpu", "load", "latency", "throughput",
            "rps", "requests per second", "response time", "performance",
            "slow", "degraded", "high memory", "out of memory", "oom",
            "gc pressure", "garbage collection", "jvm", "metrics"
    );

    private static final List<String> LOG_ERROR_KEYWORDS = List.of(
            "error", "exception", "fail", "failure", "stack trace", "stacktrace",
            "404", "500", "503", "null pointer", "npe", "timeout", "timed out",
            "connection refused", "coupon", "invalid", "rejected", "crash", "down"
    );

    private static final List<String> INVESTIGATION_KEYWORDS = List.of(
            "investigate", "debug", "diagnose", "root cause", "why is", "why are",
            "what happened", "correlation", "request id", "slow request", "high latency",
            "what is wrong", "whats wrong", "issue", "problem", "incident"
    );

    private static final List<String> HEAP_USAGE_KEYWORDS = List.of(
            "usage", "percent", "percentage", "%", "how much", "current", "right now"
    );

    private boolean matchesAny(String text, List<String> keywords) {
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    /**
     * Classifies the query and populates fetch flags on the InvestigationContext.
     */
    public void classify(String query, InvestigationContext ctx) {
        boolean needsLogs       = matchesAny(query, LOG_ERROR_KEYWORDS) || matchesAny(query, INVESTIGATION_KEYWORDS);
        boolean needsMonitoring = matchesAny(query, MONITORING_KEYWORDS) || matchesAny(query, INVESTIGATION_KEYWORDS);
        boolean heapQuery       = matchesAny(query, MONITORING_KEYWORDS) && query.toLowerCase().contains("heap");
        boolean heapPercentQuery = heapQuery && matchesAny(query, HEAP_USAGE_KEYWORDS);

        // fallback: fetch everything if no keywords matched
        if (!needsLogs && !needsMonitoring) {
            needsLogs = true;
            needsMonitoring = true;
        }

        ctx.setFetchLogs(needsLogs);
        ctx.setFetchErrorLogs(needsLogs);
        ctx.setFetchHeapMetrics(needsMonitoring || heapPercentQuery);
        ctx.setFetchHeapMaxMetrics(needsMonitoring || heapPercentQuery);
        ctx.setFetchThreadMetrics(needsMonitoring && !heapPercentQuery);
        ctx.setFetchRequestRate(needsMonitoring && !heapPercentQuery);
        ctx.setHeapUsagePercentQuery(heapPercentQuery);
    }
}
