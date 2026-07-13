package com.amol.microservices.observability.dto;

import java.util.List;

public record LatencyPercentilesResponseDto(String service, List<PercentileSeriesDto> percentiles) {
}
