package com.amol.microservices.observability.dto;

import java.time.Instant;
import java.util.List;

public record LogEntryDto(Instant timestamp, String service, String level, String message) {}

public record LogsResponseDto(String requestId, List<LogEntryDto> logs) {}

public record MetricPointDto(Instant timestamp, double value) {}

public record MetricsResponseDto(String service, String metric, List<MetricPointDto> points) {}

public record ServicesResponseDto(List<String> services) {}

public record ErrorResponseDto(String error, String detail) {}
