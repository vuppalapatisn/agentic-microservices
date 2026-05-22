from app.correlation.engine import CorrelationEngine
from app.models.schemas import InvestigationContext, MetricFinding


def _metric(value: float) -> MetricFinding:
    return MetricFinding(timestamp="2026-01-01T00:00:00Z", value=value)


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
