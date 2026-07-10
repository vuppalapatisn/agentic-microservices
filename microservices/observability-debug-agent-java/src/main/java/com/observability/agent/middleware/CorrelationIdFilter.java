package com.observability.agent.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads or generates an X-Correlation-Id per request and adds it
 * to both the MDC (for log enrichment) and the response headers.
 * Mirrors Python CorrelationIdMiddleware.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            try {
                UUID.fromString(correlationId);
            } catch (IllegalArgumentException e) {
                correlationId = UUID.randomUUID().toString();
            }
        }

        request.setAttribute("correlationId", correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        org.slf4j.MDC.put("correlationId", correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove("correlationId");
        }
    }
}
