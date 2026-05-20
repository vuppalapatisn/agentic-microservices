from __future__ import annotations

from contextvars import ContextVar
from uuid import UUID, uuid4

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

CORRELATION_HEADER = "X-Correlation-Id"
correlation_id_ctx: ContextVar[str | None] = ContextVar("correlation_id", default=None)


def get_correlation_id() -> str | None:
    return correlation_id_ctx.get()


def resolve_correlation_id(header_value: str | None) -> str:
    if not header_value or not header_value.strip():
        return str(uuid4())
    trimmed = header_value.strip()
    try:
        UUID(trimmed)
        return trimmed
    except ValueError:
        return str(uuid4())


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        correlation_id = resolve_correlation_id(request.headers.get(CORRELATION_HEADER))
        token = correlation_id_ctx.set(correlation_id)
        request.state.correlation_id = correlation_id
        try:
            response = await call_next(request)
            response.headers[CORRELATION_HEADER] = correlation_id
            return response
        finally:
            correlation_id_ctx.reset(token)
