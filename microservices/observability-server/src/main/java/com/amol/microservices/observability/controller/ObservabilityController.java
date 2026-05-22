package com.amol.microservices.observability.controller;

import com.amol.microservices.observability.dto.LogsResponseDto;
import com.amol.microservices.observability.dto.MetricsResponseDto;
import com.amol.microservices.observability.dto.ServicesResponseDto;
import com.amol.microservices.observability.service.ObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@Tag(name = "Observability", description = "Logs and metrics from Loki and Prometheus")
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final ObservabilityService service;

    public ObservabilityController(ObservabilityService service) {
        this.service = service;
    }

    @Operation(summary = "Get logs by correlation ID (searches Loki across ecommerce and observability)")
    @GetMapping("/logs/request/{correlationId}")
    public ResponseEntity<LogsResponseDto> logsByCorrelationId(
            @Parameter(description = "X-Correlation-Id value from traffic or service logs") @PathVariable String correlationId,
                                                           @RequestParam(required = false) String startTime,
                                                           @RequestParam(required = false) String endTime) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        LogsResponseDto resp = service.getLogsByRequestId(correlationId, start, end);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get logs for a service")
    @GetMapping("/logs/service/{serviceName}")
    public ResponseEntity<LogsResponseDto> logsByService(
            @Parameter(example = "ecommerce-service") @PathVariable @NotBlank String serviceName,
                                                         @RequestParam(required = false) String startTime,
                                                         @RequestParam(required = false) String endTime) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        LogsResponseDto resp = service.getLogsByService(serviceName, start, end);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get error and warn logs for a service")
    @GetMapping("/logs/errors/{serviceName}")
    public ResponseEntity<LogsResponseDto> errorLogsByService(
            @Parameter(example = "ecommerce-service") @PathVariable @NotBlank String serviceName,
                                                              @RequestParam(required = false) String startTime,
                                                              @RequestParam(required = false) String endTime) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        LogsResponseDto resp = service.getErrorLogsByService(serviceName, start, end);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get JVM heap metrics for a service")
    @GetMapping("/metrics/heap/{serviceName}")
    public ResponseEntity<MetricsResponseDto> heapMetrics(
            @Parameter(example = "ecommerce-service") @PathVariable @NotBlank String serviceName,
                                                          @RequestParam(required = false) String startTime,
                                                          @RequestParam(required = false) String endTime,
                                                          @RequestParam(required = false) Integer stepSeconds) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        if (stepSeconds != null && stepSeconds <= 0) throw new IllegalArgumentException("stepSeconds must be > 0");
        MetricsResponseDto resp = service.getHeapMetrics(serviceName, start, end, stepSeconds);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get JVM max heap metrics for a service")
    @GetMapping("/metrics/heap-max/{serviceName}")
    public ResponseEntity<MetricsResponseDto> heapMaxMetrics(
            @Parameter(example = "ecommerce-service") @PathVariable @NotBlank String serviceName,
                                                            @RequestParam(required = false) String startTime,
                                                            @RequestParam(required = false) String endTime,
                                                            @RequestParam(required = false) Integer stepSeconds) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        if (stepSeconds != null && stepSeconds <= 0) throw new IllegalArgumentException("stepSeconds must be > 0");
        MetricsResponseDto resp = service.getHeapMaxMetrics(serviceName, start, end, stepSeconds);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get JVM thread metrics for a service")
    @GetMapping("/metrics/threads/{serviceName}")
    public ResponseEntity<MetricsResponseDto> threadMetrics(
            @Parameter(example = "ecommerce-service") @PathVariable @NotBlank String serviceName,
                                                            @RequestParam(required = false) String startTime,
                                                            @RequestParam(required = false) String endTime,
                                                            @RequestParam(required = false) Integer stepSeconds) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        if (stepSeconds != null && stepSeconds <= 0) throw new IllegalArgumentException("stepSeconds must be > 0");
        MetricsResponseDto resp = service.getThreadMetrics(serviceName, start, end, stepSeconds);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get HTTP request rate metrics for a service")
    @GetMapping("/metrics/request-rate/{serviceName}")
    public ResponseEntity<MetricsResponseDto> requestRateMetrics(
            @Parameter(example = "ecommerce-service") @PathVariable @NotBlank String serviceName,
                                                                 @RequestParam(required = false) String startTime,
                                                                 @RequestParam(required = false) String endTime,
                                                                 @RequestParam(required = false) Integer stepSeconds) {
        Instant start = parseOrNull(startTime);
        Instant end = parseOrNull(endTime);
        validateRange(start, end);
        if (stepSeconds != null && stepSeconds <= 0) throw new IllegalArgumentException("stepSeconds must be > 0");
        MetricsResponseDto resp = service.getRequestRateMetrics(serviceName, start, end, stepSeconds);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "List observable services")
    @GetMapping("/services")
    public ResponseEntity<ServicesResponseDto> services() {
        ServicesResponseDto resp = service.listObservableServices();
        return ResponseEntity.ok(resp);
    }

    private Instant parseOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid ISO timestamp: " + s);
        }
    }

    private void validateRange(Instant start, Instant end) {
        if (start != null && end != null && start.isAfter(end)) throw new IllegalArgumentException("startTime must be before endTime");
    }
}
