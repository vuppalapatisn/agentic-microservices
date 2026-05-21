from __future__ import annotations

import asyncio
from time import perf_counter

import httpx

from app.config.settings import Settings
from app.logging.json_logger import get_logger
from app.middleware.correlation import CORRELATION_HEADER, get_correlation_id
from app.models.schemas import LogFinding, MetricFinding


logger = get_logger("talk-to-observability-agent.mcp")


class ObservabilityAgentClient:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.base_url = settings.observability_agent_base_url.rstrip("/")
        self.client = httpx.AsyncClient(timeout=settings.request_timeout_seconds)

    async def validate_dependencies(self) -> None:
        if not self.settings.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is not configured.")

        url = f"{self.base_url}/api/observability/services"
        max_attempts = self.settings.startup_validation_retries
        retry_seconds = self.settings.startup_validation_retry_seconds
        last_error: Exception | None = None

        for attempt in range(1, max_attempts + 1):
            start = perf_counter()
            try:
                response = await self.client.get(url, headers=self._request_headers())
                response.raise_for_status()
                duration_ms = round((perf_counter() - start) * 1000, 2)
                logger.info(
                    "observability_agent_validation_complete",
                    extra={
                        "service": "talk-to-observability-agent",
                        "correlationId": get_correlation_id(),
                        "durationMs": duration_ms,
                        "attempt": attempt,
                    },
                )
                return
            except httpx.HTTPStatusError as exc:
                body = exc.response.text[:500] if exc.response is not None else ""
                raise RuntimeError(
                    f"observability-agent returned {exc.response.status_code} during startup validation: {body}"
                ) from exc
            except httpx.HTTPError as exc:
                last_error = exc
                if attempt >= max_attempts:
                    break
                logger.warning(
                    "observability_agent_validation_retry",
                    extra={
                        "service": "talk-to-observability-agent",
                        "correlationId": get_correlation_id(),
                        "attempt": attempt,
                        "maxAttempts": max_attempts,
                        "retryInSeconds": retry_seconds,
                        "observabilityAgentBaseUrl": self.base_url,
                        "error": str(exc),
                    },
                )
                await asyncio.sleep(retry_seconds)

        raise RuntimeError(
            f"observability-agent is unavailable during startup validation after {max_attempts} attempts: {last_error}"
        ) from last_error

    async def list_observable_services(self) -> list[str]:
        payload = await self._get_json("/api/observability/services")
        return payload.get("services", [])

    async def get_logs_by_correlation_id(self, correlation_id: str, start_time: str, end_time: str) -> list[LogFinding]:
        payload = await self._get_json(
            f"/api/observability/logs/request/{correlation_id}",
            params={"startTime": start_time, "endTime": end_time},
        )
        return [LogFinding.model_validate(item) for item in payload.get("logs", [])]

    async def get_logs_by_service(self, service_name: str, start_time: str, end_time: str) -> list[LogFinding]:
        payload = await self._get_json(
            f"/api/observability/logs/service/{service_name}",
            params={"startTime": start_time, "endTime": end_time},
        )
        return [LogFinding.model_validate(item) for item in payload.get("logs", [])]

    async def get_error_logs_by_service(self, service_name: str, start_time: str, end_time: str) -> list[LogFinding]:
        payload = await self._get_json(
            f"/api/observability/logs/errors/{service_name}",
            params={"startTime": start_time, "endTime": end_time},
        )
        return [LogFinding.model_validate(item) for item in payload.get("logs", [])]

    async def get_heap_metrics(self, service_name: str, start_time: str, end_time: str, step_seconds: int) -> list[MetricFinding]:
        payload = await self._get_json(
            f"/api/observability/metrics/heap/{service_name}",
            params={"startTime": start_time, "endTime": end_time, "stepSeconds": step_seconds},
        )
        return [MetricFinding.model_validate(item) for item in payload.get("points", [])]

    async def get_heap_max_metrics(
        self, service_name: str, start_time: str, end_time: str, step_seconds: int
    ) -> list[MetricFinding]:
        payload = await self._get_json(
            f"/api/observability/metrics/heap-max/{service_name}",
            params={"startTime": start_time, "endTime": end_time, "stepSeconds": step_seconds},
        )
        return [MetricFinding.model_validate(item) for item in payload.get("points", [])]

    async def get_thread_metrics(self, service_name: str, start_time: str, end_time: str, step_seconds: int) -> list[MetricFinding]:
        payload = await self._get_json(
            f"/api/observability/metrics/threads/{service_name}",
            params={"startTime": start_time, "endTime": end_time, "stepSeconds": step_seconds},
        )
        return [MetricFinding.model_validate(item) for item in payload.get("points", [])]

    async def get_request_rate(self, service_name: str, start_time: str, end_time: str, step_seconds: int) -> list[MetricFinding]:
        payload = await self._get_json(
            f"/api/observability/metrics/request-rate/{service_name}",
            params={"startTime": start_time, "endTime": end_time, "stepSeconds": step_seconds},
        )
        return [MetricFinding.model_validate(item) for item in payload.get("points", [])]

    def _request_headers(self) -> dict[str, str]:
        headers: dict[str, str] = {}
        correlation_id = get_correlation_id()
        if correlation_id:
            headers[CORRELATION_HEADER] = correlation_id
        return headers

    async def _get_json(self, path: str, params: dict | None = None) -> dict:
        start = perf_counter()
        correlation_id = get_correlation_id()
        try:
            response = await self.client.get(
                f"{self.base_url}{path}",
                params=params,
                headers=self._request_headers(),
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as exc:
            body = exc.response.text[:500] if exc.response is not None else ""
            raise RuntimeError(
                f"observability-agent returned {exc.response.status_code} for {path}: {body}"
            ) from exc
        except httpx.HTTPError as exc:
            raise RuntimeError(f"observability-agent is unavailable for {path}: {exc}") from exc
        finally:
            duration_ms = round((perf_counter() - start) * 1000, 2)
            logger.info(
                "telemetry_fetch_complete",
                extra={
                    "service": "talk-to-observability-agent",
                    "correlationId": correlation_id,
                    "durationMs": duration_ms,
                    "query": path,
                },
            )

    async def aclose(self) -> None:
        await self.client.aclose()
