from time import perf_counter

from fastapi import APIRouter, HTTPException, Request

from app.graph.workflow import InvestigationWorkflow
from app.logging.json_logger import get_logger
from app.middleware.correlation import resolve_correlation_id
from app.models.schemas import InvestigationRequest, InvestigationResponse


router = APIRouter()
logger = get_logger("observability-debug-agent.api")
workflow = InvestigationWorkflow()


@router.get("/health")
async def health() -> dict:
    return {"status": "UP"}


@router.post("/api/v1/investigate", response_model=InvestigationResponse)
async def investigate(request: InvestigationRequest, http_request: Request) -> InvestigationResponse:
    correlation_id = getattr(http_request.state, "correlation_id", None) or resolve_correlation_id(
        http_request.headers.get("X-Correlation-Id")
    )
    start = perf_counter()
    try:
        response = await workflow.run(request, correlation_id)
    except ValueError as exc:
        logger.warning(
            "investigation_failed",
            extra={
                "service": "observability-debug-agent",
                "correlationId": correlation_id,
                "query": request.query,
                "error": str(exc),
            },
        )
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RuntimeError as exc:
        logger.error(
            "investigation_failed",
            extra={
                "service": "observability-debug-agent",
                "correlationId": correlation_id,
                "query": request.query,
                "error": str(exc),
            },
        )
        raise HTTPException(
            status_code=503,
            detail=str(exc),
            headers={"X-Correlation-Id": correlation_id},
        ) from exc
    except Exception as exc:
        logger.error(
            "investigation_failed",
            extra={
                "service": "observability-debug-agent",
                "correlationId": correlation_id,
                "query": request.query,
                "error": str(exc),
            },
        )
        raise HTTPException(
            status_code=500,
            detail=str(exc),
            headers={"X-Correlation-Id": correlation_id},
        ) from exc

    duration_ms = round((perf_counter() - start) * 1000, 2)
    logger.info(
        "investigation_complete",
        extra={
            "service": "observability-debug-agent",
            "correlationId": correlation_id,
            "investigationId": correlation_id,
            "query": request.query,
            "durationMs": duration_ms,
        },
    )
    return response
