from __future__ import annotations

from datetime import UTC, date, datetime, timedelta
import re
from time import perf_counter
from typing import Any, Literal, TypedDict

from langgraph.graph import END, StateGraph

from app.config.settings import get_settings
from app.correlation.engine import CorrelationEngine
from app.graph.classification import classify_investigation
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
from app.logging.json_logger import get_logger
from app.util.formatting import format_bytes, format_percent
from app.util.grafana_links import build_dashboard_url, build_loki_explore_url


logger = get_logger("observability-debug-agent.graph")


SERVICE_ALIASES = {
    "ecommerce": "ecommerce-service",
    "product": "product-service",
    "images": "images-service",
}

CORRELATION_UUID = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    re.IGNORECASE,
)


class InvestigationState(TypedDict, total=False):
    request: InvestigationRequest
    investigation_id: str
    query: str
    service_name: str
    request_id: str | None
    start_time: str
    end_time: str
    issue_type: str
    needs_logs: bool
    needs_monitoring: bool
    heap_usage_percent_query: bool
    fetch_logs: bool
    fetch_error_logs: bool
    fetch_heap_metrics: bool
    fetch_heap_max_metrics: bool
    fetch_thread_metrics: bool
    fetch_request_rate: bool
    logs: list[LogFinding]
    error_logs: list[LogFinding]
    heap_metrics: list[MetricFinding]
    heap_max_metrics: list[MetricFinding]
    thread_metrics: list[MetricFinding]
    request_rate_metrics: list[MetricFinding]
    correlation: CorrelationFinding
    summary: str
    probable_root_cause: str
    evidence: list[str]
    grafana_explore_url: str
    grafana_dashboard_url: str


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
        graph.add_conditional_edges(
            "build_investigation_plan_node",
            self._route_after_plan,
            {
                "fetch_logs_node": "fetch_logs_node",
                "fetch_heap_metrics_node": "fetch_heap_metrics_node",
                "correlation_node": "correlation_node",
            },
        )
        graph.add_conditional_edges(
            "fetch_logs_node",
            self._route_after_logs,
            {
                "fetch_error_logs_node": "fetch_error_logs_node",
                "correlation_node": "correlation_node",
            },
        )
        graph.add_conditional_edges(
            "fetch_error_logs_node",
            self._route_after_error_logs,
            {
                "fetch_heap_metrics_node": "fetch_heap_metrics_node",
                "correlation_node": "correlation_node",
            },
        )
        graph.add_conditional_edges(
            "fetch_heap_metrics_node",
            self._route_after_heap,
            {
                "fetch_thread_metrics_node": "fetch_thread_metrics_node",
                "correlation_node": "correlation_node",
            },
        )
        graph.add_edge("fetch_thread_metrics_node", "fetch_request_rate_node")
        graph.add_edge("fetch_request_rate_node", "correlation_node")
        graph.add_edge("correlation_node", "reasoning_node")
        graph.add_edge("reasoning_node", "response_node")
        graph.add_edge("response_node", END)
        return graph.compile()

    @staticmethod
    def _route_after_plan(
        state: InvestigationState,
    ) -> Literal["fetch_logs_node", "fetch_heap_metrics_node", "correlation_node"]:
        if state.get("needs_logs"):
            return "fetch_logs_node"
        if state.get("needs_monitoring"):
            return "fetch_heap_metrics_node"
        return "correlation_node"

    @staticmethod
    def _route_after_logs(
        state: InvestigationState,
    ) -> Literal["fetch_error_logs_node", "correlation_node"]:
        if state.get("fetch_error_logs"):
            return "fetch_error_logs_node"
        return "correlation_node"

    @staticmethod
    def _route_after_error_logs(
        state: InvestigationState,
    ) -> Literal["fetch_heap_metrics_node", "correlation_node"]:
        if state.get("fetch_heap_metrics"):
            return "fetch_heap_metrics_node"
        return "correlation_node"

    @staticmethod
    def _route_after_heap(
        state: InvestigationState,
    ) -> Literal["fetch_thread_metrics_node", "correlation_node"]:
        if state.get("fetch_thread_metrics"):
            return "fetch_thread_metrics_node"
        return "correlation_node"

    @staticmethod
    def _node_update_summary(node_output: dict[str, Any]) -> dict[str, Any]:
        summary: dict[str, Any] = {}
        for key, value in node_output.items():
            if key == "request":
                continue
            if isinstance(value, list):
                summary[f"{key}Count"] = len(value)
            elif isinstance(value, bool):
                summary[key] = value
            elif isinstance(value, (str, int, float)) or value is None:
                summary[key] = value
        return summary

    async def run(self, request: InvestigationRequest, correlation_id: str) -> InvestigationResponse:
        state: InvestigationState = {
            "request": request,
            "investigation_id": correlation_id,
            "query": request.query,
        }
        run_start = perf_counter()
        merged: InvestigationState = dict(state)
        nodes_executed: list[str] = []

        if self.settings.langgraph_debug:
            logger.info(
                "langgraph_run_start",
                extra={
                    "correlationId": correlation_id,
                    "investigationId": correlation_id,
                    "query": request.query,
                },
            )

        async for update in self.graph.astream(state, stream_mode="updates"):
            for node_name, node_output in update.items():
                merged.update(node_output)
                nodes_executed.append(node_name)
                if not self.settings.langgraph_debug:
                    continue
                extra: dict[str, Any] = {
                    "correlationId": correlation_id,
                    "investigationId": correlation_id,
                    "node": node_name,
                    "nodeSummary": self._node_update_summary(node_output),
                }
                if "needs_logs" in node_output:
                    extra["needsLogs"] = node_output["needs_logs"]
                if "needs_monitoring" in node_output:
                    extra["needsMonitoring"] = node_output["needs_monitoring"]
                logger.info("langgraph_node_complete", extra=extra)

        if self.settings.langgraph_debug:
            logger.info(
                "langgraph_run_complete",
                extra={
                    "correlationId": correlation_id,
                    "investigationId": correlation_id,
                    "durationMs": round((perf_counter() - run_start) * 1000, 2),
                    "nodesExecuted": nodes_executed,
                },
            )

        result = merged
        return InvestigationResponse(
            investigationId=correlation_id,
            correlationId=correlation_id,
            summary=result["summary"],
            probableRootCause=result["probable_root_cause"],
            evidence=result["evidence"],
            grafanaExploreUrl=result.get("grafana_explore_url"),
            grafanaDashboardUrl=result.get("grafana_dashboard_url"),
        )

    async def parse_query_node(self, state: InvestigationState) -> InvestigationState:
        query = state["query"].strip()
        lowered = query.lower()
        request_match = re.search(
            r"(request id|requestid|correlation id|correlationid)\s*[:=]?\s*([0-9a-f\-]+)",
            query,
            re.IGNORECASE,
        )
        request_id = request_match.group(2) if request_match else None
        if not request_id:
            uuid_match = CORRELATION_UUID.search(query)
            request_id = uuid_match.group(0) if uuid_match else None
        request = state.get("request")
        if not request_id and request is not None and request.correlation_id:
            request_id = request.correlation_id

        issue_type = "general"
        if "timeout" in lowered:
            issue_type = "timeout"
        elif "latency" in lowered or "slow" in lowered or "slowness" in lowered:
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
        lowered = state["query"].lower()
        for alias, service_name in SERVICE_ALIASES.items():
            if alias in lowered or service_name in lowered:
                return {"service_name": service_name}
        return {"service_name": "ecommerce-service"}

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
        return classify_investigation(state["query"])

    async def fetch_logs_node(self, state: InvestigationState) -> InvestigationState:
        logs: list[LogFinding] = []
        if state.get("fetch_logs"):
            if state.get("request_id"):
                logs = await self.client.get_logs_by_correlation_id(
                    state["request_id"], state["start_time"], state["end_time"]
                )
            else:
                logs = await self.client.get_logs_by_service(
                    state["service_name"], state["start_time"], state["end_time"]
                )
        return {"logs": logs}

    async def fetch_error_logs_node(self, state: InvestigationState) -> InvestigationState:
        error_logs: list[LogFinding] = []
        if state.get("fetch_error_logs"):
            error_logs = await self.client.get_error_logs_by_service(
                state["service_name"], state["start_time"], state["end_time"]
            )
        return {"error_logs": error_logs}

    async def fetch_heap_metrics_node(self, state: InvestigationState) -> InvestigationState:
        heap_metrics: list[MetricFinding] = []
        heap_max_metrics: list[MetricFinding] = []
        if state.get("fetch_heap_metrics"):
            heap_metrics = await self.client.get_heap_metrics(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        if state.get("fetch_heap_max_metrics"):
            heap_max_metrics = await self.client.get_heap_max_metrics(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        return {"heap_metrics": heap_metrics, "heap_max_metrics": heap_max_metrics}

    async def fetch_thread_metrics_node(self, state: InvestigationState) -> InvestigationState:
        metrics: list[MetricFinding] = []
        if state.get("fetch_thread_metrics"):
            metrics = await self.client.get_thread_metrics(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        return {"thread_metrics": metrics}

    async def fetch_request_rate_node(self, state: InvestigationState) -> InvestigationState:
        metrics: list[MetricFinding] = []
        if state.get("fetch_request_rate"):
            metrics = await self.client.get_request_rate(
                state["service_name"], state["start_time"], state["end_time"], 30
            )
        return {"request_rate_metrics": metrics}

    async def correlation_node(self, state: InvestigationState) -> InvestigationState:
        context = InvestigationContext(
            service_name=state["service_name"],
            request_id=state.get("request_id"),
            start_time=state["start_time"],
            end_time=state["end_time"],
            logs=state.get("logs", []),
            error_logs=state.get("error_logs", []),
            heap_metrics=state.get("heap_metrics", []),
            heap_max_metrics=state.get("heap_max_metrics", []),
            thread_metrics=state.get("thread_metrics", []),
            request_rate_metrics=state.get("request_rate_metrics", []),
            heap_usage_percent_query=bool(state.get("heap_usage_percent_query")),
        )
        correlation = self.correlation_engine.correlate(context)
        return {"correlation": correlation}

    async def reasoning_node(self, state: InvestigationState) -> InvestigationState:
        correlation = state["correlation"]
        merged_logs = state.get("logs", []) + state.get("error_logs", [])
        mode = "default"
        if state.get("heap_usage_percent_query"):
            mode = "heap_percent"
        elif state.get("needs_logs") and not state.get("needs_monitoring"):
            mode = "error_logs"

        prompt_payload: dict[str, Any] = {
            "service": state["service_name"],
            "query": state["query"],
            "issueType": state["issue_type"],
            "requestId": state.get("request_id"),
            "startTime": state["start_time"],
            "endTime": state["end_time"],
            "probableRootCause": correlation.probable_root_cause,
            "evidence": correlation.evidence,
        }
        if mode == "error_logs":
            prompt_payload["logs"] = [log.model_dump() for log in merged_logs]
        if mode == "heap_percent":
            used_val = self.correlation_engine._latest(state.get("heap_metrics", []))
            max_val = self.correlation_engine._latest(state.get("heap_max_metrics", []))
            if used_val is not None and max_val is not None and max_val > 0:
                percent = (used_val / max_val) * 100
                prompt_payload["heapUsagePercent"] = format_percent(percent)
                prompt_payload["heapUsed"] = format_bytes(used_val)
                prompt_payload["heapMax"] = format_bytes(max_val)
            prompt_payload["evidence"] = correlation.evidence

        summary = await self.reasoning_service.summarize(build_reasoning_messages(prompt_payload, mode=mode))
        return {
            "summary": summary,
            "probable_root_cause": correlation.probable_root_cause,
            "evidence": correlation.evidence,
        }

    async def response_node(self, state: InvestigationState) -> InvestigationState:
        start = self._parse_iso_time(state["start_time"])
        end = self._parse_iso_time(state["end_time"])
        grafana_api = self.settings.grafana_api_base_url
        grafana_ui = self.settings.grafana_base_url

        loki_url = None
        if state.get("needs_logs") and state.get("request_id"):
            loki_url = build_loki_explore_url(
                grafana_ui,
                start,
                end,
                correlation_id=state.get("request_id"),
                fallback_loki_uid=self.settings.grafana_loki_datasource_uid,
                api_base_url=grafana_api,
            )

        dashboard_url = None
        if state.get("needs_monitoring"):
            dashboard_url = build_dashboard_url(
                grafana_ui,
                start,
                end,
                fallback_dashboard_uid=self.settings.grafana_dashboard_uid,
                api_base_url=grafana_api,
            )

        summary = state["summary"].rstrip(".")
        if loki_url and loki_url not in summary:
            summary = f"{summary}. View correlated logs in Grafana Explore: {loki_url}."
        if dashboard_url and dashboard_url not in summary:
            summary = f"{summary} View JVM metrics for the incident window: {dashboard_url}."

        return {
            "grafana_explore_url": loki_url,
            "grafana_dashboard_url": dashboard_url,
            "summary": summary,
        }

    @staticmethod
    def _parse_iso_time(value: str) -> datetime:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))

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
