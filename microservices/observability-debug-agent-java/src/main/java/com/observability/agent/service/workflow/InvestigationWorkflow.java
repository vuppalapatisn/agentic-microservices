package com.observability.agent.service.workflow;

import com.observability.agent.classification.InvestigationClassifier;
import com.observability.agent.client.ObservabilityClient;
import com.observability.agent.correlation.CorrelationEngine;
import com.observability.agent.model.*;
import com.observability.agent.service.ReasoningService;
import com.observability.agent.util.GrafanaLinks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the full investigation pipeline.
 * Mirrors the LangGraph StateGraph from Python workflow.py:
 *
 *   parse -> identify service -> time range -> classify ->
 *   [parallel fetch: logs, error logs, heap, threads, rps] ->
 *   correlation -> reasoning -> response
 */
@Service
public class InvestigationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(InvestigationWorkflow.class);

    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "(ecommerce[- ]service|product[- ]service|images[- ]service|order[- ]service|coupon[- ]service)",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> SERVICE_ALIASES = Map.of(
            "ecommerce",  "ecommerce-service",
            "product",    "product-service",
            "image",      "images-service",
            "images",     "images-service",
            "order",      "ecommerce-service",
            "coupon",     "ecommerce-service"
    );

    private final ObservabilityClient observabilityClient;
    private final InvestigationClassifier classifier;
    private final CorrelationEngine correlationEngine;
    private final ReasoningService reasoningService;
    private final GrafanaLinks grafanaLinks;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public InvestigationWorkflow(ObservabilityClient observabilityClient,
                                 InvestigationClassifier classifier,
                                 CorrelationEngine correlationEngine,
                                 ReasoningService reasoningService,
                                 GrafanaLinks grafanaLinks) {
        this.observabilityClient = observabilityClient;
        this.classifier          = classifier;
        this.correlationEngine   = correlationEngine;
        this.reasoningService    = reasoningService;
        this.grafanaLinks        = grafanaLinks;
    }

    public InvestigationResponse run(InvestigationRequest request, String correlationId) {
        long start = System.currentTimeMillis();
        String investigationId = UUID.randomUUID().toString();

        InvestigationContext ctx = new InvestigationContext();
        ctx.setQuery(request.getQuery());
        ctx.setRequestId(resolveRequestId(request.getQuery(), request.getCorrelationId()));

        // 1. identify service
        ctx.setServiceName(identifyService(request.getQuery()));

        // 2. time range (last 30 minutes)
        Instant now   = Instant.now();
        Instant start30 = now.minus(30, ChronoUnit.MINUTES);
        ctx.setStartTime(start30.toString());
        ctx.setEndTime(now.toString());

        // 3. classify query -> set fetch flags
        classifier.classify(request.getQuery(), ctx);

        // 4. parallel data fetch
        fetchData(ctx);

        // 5. correlation scoring
        CorrelationFinding correlation = correlationEngine.correlate(ctx);

        // 6. Spring AI reasoning
        String summary = reasoningService.summarize(ctx, correlation);

        // 7. build Grafana links
        String exploreUrl   = grafanaLinks.buildLokiExploreUrl(
                ctx.getRequestId(), ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime());
        String dashboardUrl = grafanaLinks.buildDashboardUrl(ctx.getStartTime(), ctx.getEndTime());

        long durationMs = System.currentTimeMillis() - start;
        log.info("{\"message\":\"investigation_complete\",\"investigationId\":\"{}\",\"correlationId\":\"{}\",\"durationMs\":{},\"probableRootCause\":\"{}\"}",
                investigationId, correlationId, durationMs, correlation.getProbableRootCause());

        return InvestigationResponse.builder()
                .investigationId(investigationId)
                .correlationId(correlationId)
                .summary(summary)
                .probableRootCause(correlation.getProbableRootCause())
                .evidence(correlation.getEvidence())
                .grafanaExploreUrl(exploreUrl)
                .grafanaDashboardUrl(dashboardUrl)
                .build();
    }

    /** Run all needed fetches in parallel using virtual threads. */
    private void fetchData(InvestigationContext ctx) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (ctx.isFetchLogs()) {
            futures.add(CompletableFuture.runAsync(() -> {
                if (ctx.getRequestId() != null && !ctx.getRequestId().isEmpty()) {
                    ctx.setLogs(observabilityClient.getLogsByCorrelationId(
                            ctx.getRequestId(), ctx.getStartTime(), ctx.getEndTime()));
                } else {
                    ctx.setLogs(observabilityClient.getLogsByService(
                            ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime()));
                }
            }, executor));
        }

        if (ctx.isFetchErrorLogs()) {
            futures.add(CompletableFuture.runAsync(() ->
                    ctx.setErrorLogs(observabilityClient.getErrorLogsByService(
                            ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime())), executor));
        }

        if (ctx.isFetchHeapMetrics()) {
            futures.add(CompletableFuture.runAsync(() ->
                    ctx.setHeapMetrics(observabilityClient.getHeapMetrics(
                            ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime())), executor));
        }

        if (ctx.isFetchHeapMaxMetrics()) {
            futures.add(CompletableFuture.runAsync(() ->
                    ctx.setHeapMaxMetrics(observabilityClient.getHeapMaxMetrics(
                            ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime())), executor));
        }

        if (ctx.isFetchThreadMetrics()) {
            futures.add(CompletableFuture.runAsync(() ->
                    ctx.setThreadMetrics(observabilityClient.getThreadMetrics(
                            ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime())), executor));
        }

        if (ctx.isFetchRequestRate()) {
            futures.add(CompletableFuture.runAsync(() ->
                    ctx.setRequestRateMetrics(observabilityClient.getRequestRate(
                            ctx.getServiceName(), ctx.getStartTime(), ctx.getEndTime())), executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private String identifyService(String query) {
        Matcher m = SERVICE_PATTERN.matcher(query);
        if (m.find()) {
            return m.group(1).toLowerCase().replace(" ", "-");
        }
        String lower = query.toLowerCase();
        for (Map.Entry<String, String> e : SERVICE_ALIASES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return "ecommerce-service";
    }

    private String resolveRequestId(String query, String headerCorrelationId) {
        if (headerCorrelationId != null && !headerCorrelationId.isBlank()) {
            return headerCorrelationId;
        }
        // extract UUID-like pattern from query text
        Pattern uuidPattern = Pattern.compile(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                Pattern.CASE_INSENSITIVE);
        Matcher m = uuidPattern.matcher(query);
        return m.find() ? m.group() : null;
    }
}
