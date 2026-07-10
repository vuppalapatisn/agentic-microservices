package com.observability.agent.controller;

import com.observability.agent.middleware.CorrelationIdFilter;
import com.observability.agent.model.InvestigationRequest;
import com.observability.agent.model.InvestigationResponse;
import com.observability.agent.service.workflow.InvestigationWorkflow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing:
 *   GET  /health
 *   POST /api/v1/investigate
 *
 * Mirrors Python app/api/routes.py.
 */
@RestController
public class InvestigationController {

    private static final Logger log = LoggerFactory.getLogger(InvestigationController.class);

    private final InvestigationWorkflow workflow;

    public InvestigationController(InvestigationWorkflow workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/api/v1/investigate")
    public ResponseEntity<?> investigate(@Valid @RequestBody InvestigationRequest request,
                                         HttpServletRequest httpRequest) {
        String correlationId = (String) httpRequest.getAttribute("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = httpRequest.getHeader(CorrelationIdFilter.CORRELATION_HEADER);
        }

        long start = System.nanoTime();
        try {
            InvestigationResponse response = workflow.run(request, correlationId);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("{\"message\":\"investigation_complete\",\"correlationId\":\"{}\",\"durationMs\":{}}", correlationId, durationMs);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("detail", e.getMessage()));

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("unavailable")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header(CorrelationIdFilter.CORRELATION_HEADER, correlationId)
                        .body(Map.of("detail", e.getMessage()));
            }
            log.error("{\"message\":\"investigation_error\",\"correlationId\":\"{}\",\"error\":\"{}\"}", correlationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(CorrelationIdFilter.CORRELATION_HEADER, correlationId)
                    .body(Map.of("detail", "Internal server error"));
        }
    }
}
