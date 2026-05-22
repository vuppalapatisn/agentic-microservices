from __future__ import annotations

from time import perf_counter

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.logging.json_logger import get_logger
from app.middleware.correlation import CORRELATION_HEADER


logger = get_logger("observability-debug-agent.http")


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        correlation_id = getattr(request.state, "correlation_id", None)
        start = perf_counter()
        response = await call_next(request)
        duration_ms = round((perf_counter() - start) * 1000, 2)
        logger.info(
            "http_request_complete",
            extra={
                "service": "observability-debug-agent",
                "correlationId": correlation_id,
                "method": request.method,
                "path": request.url.path,
                "statusCode": response.status_code,
                "durationMs": duration_ms,
            },
        )
        if correlation_id:
            response.headers[CORRELATION_HEADER] = correlation_id
        return response
