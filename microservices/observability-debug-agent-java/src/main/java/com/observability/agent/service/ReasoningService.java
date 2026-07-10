package com.observability.agent.service;

import com.observability.agent.model.CorrelationFinding;
import com.observability.agent.model.InvestigationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Calls Spring AI ChatClient (OpenAI gpt-4.1-mini) to generate the
 * human-readable investigation summary.
 * Mirrors Python ReasoningService + prompts/reasoning.py.
 */
@Service
public class ReasoningService {

    private static final Logger log = LoggerFactory.getLogger(ReasoningService.class);

    private final ChatClient chatClient;

    public ReasoningService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String summarize(InvestigationContext ctx, CorrelationFinding correlation) {
        String systemPrompt = buildSystemPrompt(ctx);
        String userPrompt   = buildUserPrompt(ctx, correlation);

        try {
            return chatClient.prompt(new Prompt(List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userPrompt))))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("{\"message\":\"reasoning_failed\",\"error\":\"{}\"}", e.getMessage());
            throw new RuntimeException("AI reasoning service unavailable: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(InvestigationContext ctx) {
        if (ctx.isHeapUsagePercentQuery()) {
            return """
                    You are an observability assistant for a Java microservices platform.
                    The developer asked specifically about heap memory usage percentage.
                    Answer with the heap usage as a percentage first (e.g. "Heap is at 72.3%"),
                    then provide the exact used and max byte values.
                    Keep the answer to 2-4 concise sentences. Do not include logs or request rates.
                    """;
        }

        boolean hasErrors = !ctx.getErrorLogs().isEmpty();
        if (hasErrors && ctx.getLogs().isEmpty() && ctx.getHeapMetrics().isEmpty()) {
            return """
                    You are an observability assistant for a Java microservices platform.
                    Analyse the provided error logs and explain:
                    1. What error occurred and which service was affected.
                    2. The probable root cause based on error messages, HTTP status codes,
                       and any stack traces.
                    3. If a downstream service (e.g. coupon-service) is mentioned, name it.
                    Be concise: 3-6 sentences. Do not speculate beyond what the logs show.
                    """;
        }

        return """
                You are an observability assistant for a Java microservices platform.
                Analyse the provided metrics, logs, and correlation findings to explain
                the probable root cause of the observed issue.
                Rules:
                - Preserve exact metric values (bytes, rps, thread counts) from the data.
                - Mention heap usage and request rate when relevant.
                - If no spike or anomaly is detected, say so clearly.
                - Be concise: 3-5 sentences.
                """;
    }

    private String buildUserPrompt(InvestigationContext ctx, CorrelationFinding correlation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Service: ").append(ctx.getServiceName()).append("\n");
        sb.append("Time range: ").append(ctx.getStartTime()).append(" to ").append(ctx.getEndTime()).append("\n");
        sb.append("Query: ").append(ctx.getQuery()).append("\n\n");

        sb.append("Probable root cause: ").append(correlation.getProbableRootCause()).append("\n");
        sb.append("Evidence:\n");
        correlation.getEvidence().forEach(e -> sb.append("  - ").append(e).append("\n"));
        sb.append("\n");

        if (!ctx.getHeapMetrics().isEmpty()) {
            sb.append("Heap metrics (").append(ctx.getHeapMetrics().size()).append(" data points)\n");
        }
        if (!ctx.getThreadMetrics().isEmpty()) {
            sb.append("Thread metrics (").append(ctx.getThreadMetrics().size()).append(" data points)\n");
        }
        if (!ctx.getRequestRateMetrics().isEmpty()) {
            sb.append("Request rate metrics (").append(ctx.getRequestRateMetrics().size()).append(" data points)\n");
        }
        if (!ctx.getErrorLogs().isEmpty()) {
            sb.append("Error logs (").append(ctx.getErrorLogs().size()).append(" entries):\n");
            ctx.getErrorLogs().stream().limit(5).forEach(l ->
                    sb.append("  [").append(l.getLevel()).append("] ").append(l.getMessage()).append("\n"));
        }

        return sb.toString();
    }
}
