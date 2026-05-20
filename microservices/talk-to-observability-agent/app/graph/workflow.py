from __future__ import annotations

from datetime import UTC, date, datetime, timedelta
import re
from typing import Any, TypedDict

from langgraph.graph import END, StateGraph

from app.config.settings import get_settings
from app.correlation.engine import CorrelationEngine
from app.mcp.observability_client import ObservabilityAgentClient
from app.models.schemas import (
    CorrelationFinding,
    InvestigationContext,
    InvestigationRequest,
    InvestigationResponse,
    LogFinding,
    MetricFinding,
)
from app.prompts.reasoning import build_reasoning_messages
from app.services.reasoning_service import ReasoningService


SERVICE_ALIASES = {
    "ecommerce": "ecommerce-service",
    "product": "product-service",
    "images": "images-service",
}


class InvestigationState(TypedDict, total=False):
    request: InvestigationRequest
    investigation_id: str
    query: str
    service_name: str
    request_id: str | None
    start_time: str
    end_time: str
    issue_type: str
    fetch_logs: bool
    fetch_error_logs: bool
    fetch_heap_metrics: bool
    fetch_thread_metrics: bool
    fetch_request_rate: bool
    available_services: list[str]
    logs: list[LogFinding]
    error_logs: list[LogFinding]
    heap_metrics: list[MetricFinding]
    thread_metrics: list[MetricFinding]
    request_rate_metrics: list[MetricFinding]
    correlation: CorrelationFinding
    summary: str
    probable_root_cause: str
    evidence: list[str]


class InvestigationWorkflow:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = ObservabilityAgentClient(self.settings)
        self.correlation_engine = CorrelationEngine()
        self.reasoning_service = ReasoningService(self.settings)
        self.graph = self._build_graph()

    def _build_graph(self):
        graph = StateGraph(InvestigationState)
        graph.add_node("parse_query_node", self.parse_query_node)
        graph.add_node("identify_service_node", self.identify_service_node)
        graph.add_node("identify_time_range_node", self.identify_time_range_node)
        graph.add_node("build_investigation_plan_node", self.build_investigation_plan_node)
        graph.add_node("fetch_logs_node", self.fetch_logs_node)
        graph.add_node("fetch_error_logs_node", self.fetch_error_logs_node)
        graph.add_node("fetch_heap_metrics_node", self.fetch_heap_metrics_node)
        graph.add_node("fetch_thread_metrics_node", self.fetch_thread_metrics_node)
        graph.add_node("fetch_request_rate_node", self.fetch_request_rate_node)
        graph.add_node("correlation_node", self.correlation_node)
        graph.add_node("reasoning_node", self.reasoning_node)
        graph.add_node("response_node", self.response_node)

        graph.set_entry_point("parse_query_node")
        graph.add_edge("parse_query_node", "identify_service_node")
        graph.add_edge("identify_service_node", "identify_time_range_node")
        graph.add_edge("identify_time_range_node", "build_investigation_plan_node")
        graph.add_edge("build_investigation_plan_node", "fetch_logs_node")
        graph.add_edge("fetch_logs_node", "fetch_error_logs_node")
        graph.add_edge("fetch_error_logs_node", "fetch_heap_metrics_node")
        graph.add_edge("fetch_heap_metrics_node", "fetch_thread_metrics_node")
        graph.add_edge("fetch_thread_metrics_node", "fetch_request_rate_node")
        graph.add_edge("fetch_request_rate_node", "correlation_node")
        graph.add_edge("correlation_node", "reasoning_node")
        graph.add_edge("reasoning_node", "response_node")
        graph.add_edge("response_node", END)
        return graph.compile()

    async def run(self, request: InvestigationRequest, investigation_id: str) -> InvestigationResponse:
        state: InvestigationState = {
            "request": request,
            "investigation_id": investigation_id,
            "query": request.query,
        }
        result = await self.graph.ainvoke(state)
        return InvestigationResponse(
            investigationId=investigation_id,
            correlationId=investigation_id,
            summary=result["summary"],
            probableRootCause=result["probable_root_cause"],
            evidence=result["evidence"],
        )

    async def parse_query_node(self, state: InvestigationState) -> InvestigationState:
        query = state["query"].strip()
        lowered = query.lower()
        request_match = re.search(r"(request id|requestid|correlation id|correlationid)\s*[:=]?\s*([A-Za-z0-9\-]+)", query, re.IGNORECASE)
        request_id = request_match.group(2) if request_match else None

        issue_type = "general"
        if "timeout" in lowered:
            issue_type = "timeout"
        elif "latency" in lowered or "slow" in lowered:
            issue_type = "latency"
        elif "heap" in lowered or "memory" in lowered:
            issue_type = "heap"
        elif "thread" in lowered:
            issue_type = "threads"
        elif "rate" in lowered or "traffic" in lowered or "load" in lowered:
            issue_type = "request-rate"
        elif "error" in lowered or "fail" in lowered:
            issue_type = "errors"

        return {
            "query": query,
            "request_id": request_id,
            "issue_type": issue_type,
        }

    async def identify_service_node(self, state: InvestigationState) -> InvestigationState:
        services = await self.client.list_observable_services()
        lowered = state["query"].lower()
        for alias, service_name in SERVICE_ALIASES.items():
            if alias in lowered or service_name in lowered:
                return {"service_name": service_name, "available_services": services}
        return {"service_name": "ecommerce-service", "available_services": services}

    async def identify_time_range_node(self, state: InvestigationState) -> InvestigationState:
        query = state["query"]
        now = datetime.now(UTC)
        start = now - timedelta(minutes=15)
        end = now

        between_match = re.search(
            r"between\s+(\d{1,2}:\d{2}\s*[APMapm]{2})\s+and\s+(\d{1,2}:\d{2}\s*[APMapm]{2})",
            query,
        )
        last_match = re.search(r"last\s+(\d+)\s+minute", query, re.IGNORECASE)
        if between_match:
            today = date.today()
            start = self._to_utc_datetime(today, between_match.group(1))
            end = self._to_utc_datetime(today, between_match.group(2))
            if end < start:
                end += timedelta(days=1)
        elif last_match:
            minutes = int(last_match.group(1))
            start = now - timedelta(minutes=minutes)

        return {
            "start_time": start.isoformat().replace("+00:00", "Z"),
            "end_time": end.isoformat().replace("+00:00", "Z"),
        }

    async def build_investigation_plan_node(self, state: InvestigationState) -> InvestigationState:
        issue_type = state["issue_type"]
        request_id = state.get("request_id")
        return {
            "fetch_logs": True,
            "fetch_error_logs": issue_type in {"timeout", "latency", "errors", "general"} or bool(request_id),
            "fetch_heap_metrics": issue_type in {"timeout", "latency", "heap", "general"},
            "fetch_thread_metrics": issue_type in {"timeout", "latency", "threads", "general"},
            "fetch_request_rate": issue_type in {"timeout", "latency", "request-rate", "errors", "general"},
        }

    async def fetch_logs_node(self, state: InvestigationState) -> InvestigationState:
        logs = []
        if state["fetch_logs"]:
            if state.get("request_id"):
                logs = await self.client.get_logs_by_request_id(
                    state["request_id"], state["start_time"], state["end_time"]
                )
            else:
                logs = await self.client.get_logs_by_service(
                    state["service_name"], state["start_time"], state["end_time"]
                )
        return {"logs": logs}

    async def fetch_error_logs_node(self, state: InvestigationState) -> InvestigationState:
        error_logs = []
        if state["fetch_error_logs"]:
            error_logs = await self.client.get_error_logs_by_service(
                state["service_name"], state["start_time"], state["end_time"]
            )
        return {"error_logs": error_logs}

    async def fetch_heap_metrics_node(self, state: InvestigationState) -> InvestigationState:
        metrics = []
        if state["fetch_heap_metrics"]:
            metrics = await self.client.get_heap_metrics(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        return {"heap_metrics": metrics}

    async def fetch_thread_metrics_node(self, state: InvestigationState) -> InvestigationState:
        metrics = []
        if state["fetch_thread_metrics"]:
            metrics = await self.client.get_thread_metrics(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        return {"thread_metrics": metrics}

    async def fetch_request_rate_node(self, state: InvestigationState) -> InvestigationState:
        metrics = []
        if state["fetch_request_rate"]:
            metrics = await self.client.get_request_rate(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        return {"request_rate_metrics": metrics}

    async def correlation_node(self, state: InvestigationState) -> InvestigationState:
        context = InvestigationContext(
            service_name=state["service_name"],
            request_id=state.get("request_id"),
            issue_type=state["issue_type"],
            start_time=state["start_time"],
            end_time=state["end_time"],
            logs=state.get("logs", []),
            error_logs=state.get("error_logs", []),
            heap_metrics=state.get("heap_metrics", []),
            thread_metrics=state.get("thread_metrics", []),
            request_rate_metrics=state.get("request_rate_metrics", []),
        )
        correlation = self.correlation_engine.correlate(context)
        return {"correlation": correlation}

    async def reasoning_node(self, state: InvestigationState) -> InvestigationState:
        correlation = state["correlation"]
        prompt_payload = {
            "service": state["service_name"],
            "query": state["query"],
            "issueType": state["issue_type"],
            "requestId": state.get("request_id"),
            "startTime": state["start_time"],
            "endTime": state["end_time"],
            "probableRootCause": correlation.probable_root_cause,
            "evidence": correlation.evidence,
        }
        summary = await self.reasoning_service.summarize(build_reasoning_messages(prompt_payload))
        return {
            "summary": summary,
            "probable_root_cause": correlation.probable_root_cause,
            "evidence": correlation.evidence,
        }

    async def response_node(self, state: InvestigationState) -> InvestigationState:
        return state

    @staticmethod
    def _to_utc_datetime(current_date: date, time_text: str) -> datetime:
        local_time = datetime.strptime(time_text.strip().upper(), "%I:%M %p")
        return datetime(
            current_date.year,
            current_date.month,
            current_date.day,
            local_time.hour,
            local_time.minute,
            tzinfo=UTC,
        )
