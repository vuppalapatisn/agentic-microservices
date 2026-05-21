from __future__ import annotations

from pydantic import BaseModel, Field


class InvestigationRequest(BaseModel):
    query: str = Field(min_length=3)
    correlation_id: str | None = Field(
        default=None,
        description="Ecommerce trace ID (from traffic script); used for Loki lookup when not in query",
        alias="correlationId",
    )

    model_config = {"populate_by_name": True}


class LogFinding(BaseModel):
    timestamp: str
    service: str
    level: str
    message: str

    @property
    def duration_ms(self) -> int | None:
        marker = "durationMs="
        if marker not in self.message:
            return None
        value = self.message.split(marker, 1)[1].split()[0]
        try:
            return int(value)
        except ValueError:
            return None


class MetricFinding(BaseModel):
    timestamp: str
    value: float


class CorrelationFinding(BaseModel):
    probable_root_cause: str
    evidence: list[str]
    tags: list[str]


class InvestigationContext(BaseModel):
    service_name: str
    request_id: str | None = None
    issue_type: str
    start_time: str
    end_time: str
    logs: list[LogFinding]
    error_logs: list[LogFinding]
    heap_metrics: list[MetricFinding]
    heap_max_metrics: list[MetricFinding] = []
    thread_metrics: list[MetricFinding]
    request_rate_metrics: list[MetricFinding]
    heap_usage_percent_query: bool = False


class InvestigationResponse(BaseModel):
    investigationId: str
    correlationId: str
    summary: str
    probableRootCause: str
    evidence: list[str]
    grafanaExploreUrl: str | None = None
    grafanaDashboardUrl: str | None = None
