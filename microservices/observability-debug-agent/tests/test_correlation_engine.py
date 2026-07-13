from app.correlation.engine import CorrelationEngine
from app.models.schemas import InvestigationContext, LatencyPercentileSeries, MetricFinding


def _metric(value: float) -> MetricFinding:
    return MetricFinding(timestamp="2026-01-01T00:00:00Z", value=value)


def _latency(label: str, quantile: float, values: list[float]) -> LatencyPercentileSeries:
    return LatencyPercentileSeries(label=label, quantile=quantile, points=[_metric(v) for v in values])


def test_thread_evidence_uses_whole_numbers_and_average_label():
    engine = CorrelationEngine()
    context = InvestigationContext(
        service_name="ecommerce-service",
        start_time="2026-01-01T00:00:00Z",
        end_time="2026-01-01T01:00:00Z",
        logs=[],
        error_logs=[],
        heap_metrics=[],
        heap_max_metrics=[],
        thread_metrics=[_metric(69.43), _metric(213.0)],
        request_rate_metrics=[],
    )
    finding = engine.correlate(context)
    thread_line = next(e for e in finding.evidence if "thread" in e.lower())
    assert "69" in thread_line and "213" in thread_line
    assert "69.43" not in thread_line
    assert "average" in thread_line.lower()
    assert "peak" in thread_line.lower()


def test_slow_investigation_includes_heap_telemetry_without_spike():
    engine = CorrelationEngine()
    context = InvestigationContext(
        service_name="ecommerce-service",
        start_time="2026-01-01T00:00:00Z",
        end_time="2026-01-01T01:00:00Z",
        logs=[],
        error_logs=[],
        heap_metrics=[_metric(50_000_000), _metric(55_000_000)],
        heap_max_metrics=[_metric(100_000_000), _metric(100_000_000)],
        thread_metrics=[],
        request_rate_metrics=[],
    )
    finding = engine.correlate(context)
    heap_lines = [e for e in finding.evidence if "heap" in e.lower()]
    assert len(heap_lines) == 1
    assert "averaged" in heap_lines[0].lower()
    assert "%" in heap_lines[0]


def test_latency_percentiles_produce_evidence_in_milliseconds():
    engine = CorrelationEngine()
    context = InvestigationContext(
        service_name="ecommerce-service",
        start_time="2026-01-01T00:00:00Z",
        end_time="2026-01-01T01:00:00Z",
        logs=[],
        error_logs=[],
        heap_metrics=[],
        heap_max_metrics=[],
        thread_metrics=[],
        request_rate_metrics=[],
        latency_percentiles=[
            _latency("http_latency_p90", 0.90, [0.12, 0.20]),
            _latency("http_latency_p95", 0.95, [0.30, 0.45]),
            _latency("http_latency_p99", 0.99, [0.90, 1.80]),
        ],
    )
    finding = engine.correlate(context)
    latency_line = next(e for e in finding.evidence if "latency" in e.lower())
    assert "P90" in latency_line and "P95" in latency_line and "P99" in latency_line
    assert "1800 ms" in latency_line  # p99 peak of 1.80s rendered in ms


def test_high_tail_latency_scores_resource_saturation():
    engine = CorrelationEngine()
    context = InvestigationContext(
        service_name="ecommerce-service",
        start_time="2026-01-01T00:00:00Z",
        end_time="2026-01-01T01:00:00Z",
        logs=[],
        error_logs=[],
        heap_metrics=[],
        heap_max_metrics=[],
        thread_metrics=[],
        request_rate_metrics=[],
        latency_percentiles=[_latency("http_latency_p99", 0.99, [1.5, 2.2])],
    )
    finding = engine.correlate(context)
    assert finding.probable_root_cause == "resource saturation"
